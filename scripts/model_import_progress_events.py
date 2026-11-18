"""Bounded, redacting parser for `uiautomator events`; no device operations.

An event is UI feedback, not proof of payload bytes, process identity or heap use.
Callers must establish listener readiness and separately correlate a single owned
copy with PID/UID and partial-candidate/memory observations.
"""
import re

from model_import_device import Failure


# Any complete real accessibility event proves delivery through the installed
# listener. It does NOT prove target focus, progress, heap use or future liveness.
_ACCESSIBILITY_EVENT = re.compile(
    rb'[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3} '
    rb'EventType: TYPE_[A-Z_]+; EventTime: [0-9]{1,19}; '
    rb'PackageName: [A-Za-z0-9_.]+; MovementGranularity: [0-9]+; Action: [0-9]+'
    rb'(?:; ContentChangeTypes: [^;\r\n]+; WindowChangeTypes: [^;\r\n]+)?'
    rb'(?:; TextChangeTypes: [^;\r\n]+)?'
    rb' \[ ClassName: [A-Za-z0-9_.$]+;[^\r\n]* \]; (?:recordCount|RecordCount): [0-9]+'
)

_EVENT = re.compile(
    rb'[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3} '
    rb'EventType: TYPE_VIEW_TEXT_CHANGED; EventTime: ([0-9]{1,19}); '
    rb'PackageName: io\.github\.greprohan\.llamadroid; '
    rb'MovementGranularity: [0-9]+; Action: [0-9]+'
    rb'(?:; ContentChangeTypes: [^;\r\n]+; WindowChangeTypes: [^;\r\n]+)?'
    rb'(?:; TextChangeTypes: [^;\r\n]+)?'
    rb' \[ ClassName: android\.widget\.TextView; Text: '
    rb'\[([0-9]{1,19}) of ([0-9]{1,19}) bytes \(([0-9]{1,3})%\)\]; '
    rb'ContentDescription: null;[^\r\n]* \]; (?:recordCount|RecordCount): 0'
)



def event_schema(line):
    """Return only allowlisted field names and structural separator metadata."""
    if len(line) > 16384 or b' EventType: ' not in line:
        raise Failure('bounded event line required for schema')
    allowed = set(('EventType EventTime PackageName MovementGranularity Action '
                   'ContentChangeTypes WindowChangeTypes TextChangeTypes ClassName Text ContentDescription '
                   'ItemCount CurrentItemIndex IsEnabled IsPassword IsChecked IsFullScreen '
                   'Scrollable Enabled Password Checked FullScreen BeforeText FromIndex ToIndex '
                   'ScrollX ScrollY MaxScrollX MaxScrollY ScrollDeltaX ScrollDeltaY AddedCount '
                   'RemovedCount ParcelableData DisplayId ConnectionId SourceWindowId '
                   'SourceNodeId SourceDisplayId recordCount RecordCount').split())
    keys = re.findall(rb'\b([A-Za-z][A-Za-z0-9_]*):', line)
    if len(keys) > 128:
        raise Failure('event schema field limit')
    footer = re.search(rb'([ \t]*\];[ \t]*(?:recordCount|RecordCount):[ \t]*)[0-9]+$', line)
    header = line.partition(b' [ ClassName:')[0]
    signed = {}
    for key in ['MovementGranularity', 'Action']:
        match = re.search(key.encode()+rb': (-?[0-9]+)(?:;|$)', header)
        signed[key] = ('negative' if match[1].startswith(b'-') else 'nonnegative') if match else 'unrecognized'
    # Before ClassName, Android emits only event metadata fields (no Text,
    # BeforeText or content descriptions). Retain bounded field labels, never values.
    header_start = header.partition(b'EventType: ')[2]
    header_keys = re.findall(rb'(?:^|; )([A-Za-z][A-Za-z0-9_]{0,47}): ', b'EventType: '+header_start)
    return dict(header_field_order=[key.decode('ascii') for key in header_keys],
                field_order=[key.decode('ascii') if key.decode('ascii') in allowed else 'UNKNOWN' for key in keys],
                footer_separator=footer[1].decode('ascii') if footer else None,
                timestamp_prefix=bool(re.match(rb'^[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3} EventType:', line)),
                header_integer_shapes=signed)


class ProgressEvents:
    def __init__(self, total, *, not_before_uptime_ms=0):
        if type(total) is not int or not 24 <= total < 2**63:
            raise Failure('positive signed-Long model size required')
        if type(not_before_uptime_ms) is not int or not 0 <= not_before_uptime_ms < 2**63:
            raise Failure('valid event uptime cutoff required')
        self._cutoff = not_before_uptime_ms
        self.total = total
        self.samples = []
        self.listener_event_seen = False
        self._pending = bytearray()
        self._received = 0
        self._last_tick = -1

    def feed(self, chunk):
        self._received += len(chunk)
        if self._received > 4*1024*1024:
            raise Failure('event transport output limit')
        # Process chunks without ever accumulating an unbounded line or retaining
        # source names, foreign event text, BeforeText or raw diagnostic output.
        for part in chunk.splitlines(keepends=True):
            self._pending.extend(part)
            if len(self._pending) > 16384:
                raise Failure('event line limit')
            if self._pending.endswith(b'\n'):
                line = bytes(self._pending[:-1]).removesuffix(b'\r')
                self._pending.clear()
                self._line(line)

    def _line(self, line):
        if _ACCESSIBILITY_EVENT.fullmatch(line):
            self.listener_event_seen = True
        match = _EVENT.fullmatch(line)
        if match is None:
            return
        tick, copied, total, percent = map(int, match.groups())
        if (tick <= self._cutoff or tick >= 2**63 or total != self.total or copied > total or
                percent != copied*100//total):
            return
        if tick < self._last_tick or (self.samples and copied < self.samples[-1]['bytes']):
            raise Failure('progress event sequence changed or reordered')
        self._last_tick = tick
        if self.samples and copied == self.samples[-1]['bytes']:
            return
        if len(self.samples) >= 512:
            raise Failure('progress sample limit')
        self.samples.append(dict(event_uptime_ms=tick, bytes=copied, total=total, percent=percent))

    def finish(self):
        if self._pending:
            raise Failure('truncated event line; observation incomplete')
