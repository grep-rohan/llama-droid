"""Synthetic AOSP event.toString format; no Android observation is fabricated."""
import unittest

from model_import_device import Failure
from model_import_progress_events import ProgressEvents, event_schema

TARGET = 'io.github.greprohan.llamadroid'
TOTAL = 2**32


def event(count=3_000_000_000, tick=1000, package=TARGET, kind='TYPE_VIEW_TEXT_CHANGED', text=None):
    text = text if text is not None else f'{count} of {TOTAL} bytes ({count*100//TOTAL}%)'
    return (f'11-18 01:00:00.123 EventType: {kind}; EventTime: {tick}; PackageName: {package}; '
            f'MovementGranularity: 0; Action: 0; ContentChangeTypes: CONTENT_CHANGE_TYPE_UNDEFINED; '
            f'WindowChangeTypes: 0 [ ClassName: android.widget.TextView; Text: [{text}]; '
            'ContentDescription: null; ItemCount: -1; CurrentItemIndex: -1; Enabled: true; '
            'BeforeText: 1 of 4294967296 bytes (0%); AddedCount: 1; RemovedCount: 1 ]; recordCount: 0\r\n').encode()


class SchemaTest(unittest.TestCase):
    def test_only_allowlisted_keys_and_separator_not_values_are_retained(self):
        raw = event(text='private-source-secret; EvilSecret: private-package-secret').rstrip(b'\r\n')
        result = event_schema(raw)
        self.assertIn('ClassName', result['field_order'])
        self.assertEqual(result['footer_separator'], ' ]; recordCount: ')
        self.assertNotIn('private', repr(result))
        self.assertNotIn('EvilSecret', repr(result))

    def test_compact_record_end_is_identified_without_text_retention(self):
        result = event_schema(event().replace(b' ]; recordCount:', b']; recordCount:').rstrip(b'\r\n'))
        self.assertEqual(result['footer_separator'], ']; recordCount: ')

    def test_non_event_or_oversized_line_rejected(self):
        for raw in [b'private source diagnostic', b'x'*16385]:
            with self.subTest(raw_size=len(raw)), self.assertRaises(Failure): event_schema(raw)


class ProgressEventsTest(unittest.TestCase):
    def setUp(self):
        self.parser = ProgressEvents(TOTAL)

    def test_exact_64bit_feedback_only(self):
        self.parser.feed(event())
        self.assertEqual(self.parser.samples, [dict(event_uptime_ms=1000, bytes=3000000000,
                                                   total=TOTAL, percent=69)])

    def test_observed_text_change_types_header_is_listener_compatible(self):
        # Actual emulator schema adds this field; numeric values below are synthetic.
        raw = event(package='other.app').replace(b'WindowChangeTypes: 0 [', b'WindowChangeTypes: 0; TextChangeTypes: 0 [')
        self.parser.feed(raw)
        self.assertTrue(self.parser.listener_event_seen)
        self.assertEqual(self.parser.samples, [])

    def test_observed_header_preserves_exact_target_progress_filter(self):
        raw = event().replace(b'WindowChangeTypes: 0 [', b'WindowChangeTypes: 0; TextChangeTypes: TEXT_CHANGE_TYPE_UNDEFINED [')
        self.parser.feed(raw)
        self.assertEqual([s['bytes'] for s in self.parser.samples], [3000000000])

    def test_unknown_extra_header_does_not_acknowledge_listener(self):
        self.parser.feed(event().replace(b'WindowChangeTypes: 0 [', b'WindowChangeTypes: 0; OtherChangeTypes: 0 ['))
        self.assertFalse(self.parser.listener_event_seen)
        self.assertEqual(self.parser.samples, [])

    def test_real_event_is_listener_evidence_not_progress_evidence(self):
        self.parser.feed(event(package='com.android.systemui', kind='TYPE_WINDOW_CONTENT_CHANGED', text='private-clock-text'))
        self.assertTrue(getattr(self.parser, 'listener_event_seen', False))
        self.assertEqual(self.parser.samples, [])

    def test_partial_or_diagnostic_lines_are_not_listener_readiness(self):
        for raw in [b'LD_ARMED\n', b'Reporting events\n', b'noise '+event(), event()[:-2]]:
            parser = ProgressEvents(TOTAL)
            parser.feed(raw)
            self.assertFalse(getattr(parser, 'listener_event_seen', False))

    def test_only_complete_event_can_acknowledge_listener(self):
        raw = event()
        self.parser.feed(raw[:-1])
        self.assertFalse(getattr(self.parser, 'listener_event_seen', False))
        self.parser.feed(raw[-1:])
        self.assertTrue(getattr(self.parser, 'listener_event_seen', False))

    def test_fragmented_crlf_and_multiple_lines(self):
        raw = event(10, 1000) + event(2**31+1, 2000)
        for byte in raw: self.parser.feed(bytes([byte]))
        self.parser.finish()
        self.assertEqual([s['bytes'] for s in self.parser.samples], [10, 2**31+1])

    def test_before_text_is_not_current_feedback(self):
        self.parser.feed(event(10))
        self.assertEqual([s['bytes'] for s in self.parser.samples], [10])

    def test_foreign_packages_types_classes_and_lookalikes_discarded(self):
        for raw in [event(package='other.app'), event(package=TARGET+'.test'),
                    event(kind='TYPE_NOTIFICATION_STATE_CHANGED'),
                    event().replace(b'android.widget.TextView', b'android.widget.EditText'),
                    b'noise '+event(), event().replace(b'PackageName:', b'UnknownName:')]:
            self.parser.feed(raw)
        self.assertEqual(self.parser.samples, [])

    def test_malformed_numeric_payloads_do_not_become_samples(self):
        for text in ['-1 of 4294967296 bytes (0%)', '4 of 4294967296 bytes (99%)',
                     '4294967297 of 4294967296 bytes (100%)', '4 of 8 bytes (50%)',
                     'source-private-name', '4 of 4294967296 bytes (0%), extra']:
            self.parser.feed(event(text=text))
        self.assertEqual(self.parser.samples, [])

    def test_duplicate_counts_are_not_extra_samples(self):
        self.parser.feed(event(10)+event(10, 2000))
        self.assertEqual(len(self.parser.samples), 1)

    def test_decreasing_bytes_reject_instead_of_new_copy(self):
        self.parser.feed(event(100))
        with self.assertRaises(Failure): self.parser.feed(event(10, 2000))

    def test_decreasing_event_time_rejected(self):
        self.parser.feed(event(100, 2000))
        with self.assertRaises(Failure): self.parser.feed(event(200, 1000))

    def test_record_list_event_not_mistaken_for_single_text_view(self):
        self.parser.feed(event().replace(b'recordCount: 0', b'recordCount: 1'))
        self.assertEqual(self.parser.samples, [])

    def test_truncated_line_is_not_accepted(self):
        self.parser.feed(event()[:-2])
        with self.assertRaises(Failure): self.parser.finish()
        self.assertEqual(self.parser.samples, [])

    def test_line_bound_applies_to_foreign_output_too(self):
        with self.assertRaises(Failure): self.parser.feed(b'x'*16385)

    def test_total_transport_budget(self):
        with self.assertRaises(Failure):
            for _ in range(4097): self.parser.feed(b'x'*1023+b'\n')

    def test_sample_count_bound(self):
        with self.assertRaises(Failure):
            for i in range(513): self.parser.feed(event(i+1, i+1))

    def test_invalid_total_rejected(self):
        for total in [0, -1, True, 2**63, '4294967296']:
            with self.subTest(total=total), self.assertRaises(Failure): ProgressEvents(total)

    def test_raw_strings_not_retained_in_result(self):
        self.parser.feed(event(text='private-source-secret')+event())
        self.assertNotIn('private-source-secret', repr(self.parser.samples))
        self.assertEqual(len(self.parser.samples), 1)


if __name__ == '__main__':
    unittest.main()
