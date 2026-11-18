"""Bounded, redacting parser for `uiautomator events`; no device operations.

An event is UI feedback, not proof of payload bytes, process identity or heap use.
Callers must establish listener readiness and separately correlate a single owned
copy with PID/UID and partial-candidate/memory observations.
"""
import re

from model_import_device import Failure


_EVENT = re.compile(
    rb'[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3} '
    rb'EventType: TYPE_VIEW_TEXT_CHANGED; EventTime: ([0-9]{1,19}); '
    rb'PackageName: io\.github\.greprohan\.llamadroid; '
    rb'MovementGranularity: [0-9]+; Action: [0-9]+'
    rb'(?:; ContentChangeTypes: [^;\r\n]+; WindowChangeTypes: [^;\r\n]+)?'
    rb' \[ ClassName: android\.widget\.TextView; Text: '
    rb'\[([0-9]{1,19}) of ([0-9]{1,19}) bytes \(([0-9]{1,3})%\)\]; '
    rb'ContentDescription: null;[^\r\n]* \]; (?:recordCount|RecordCount): 0'
)



class ProgressEvents:
    def __init__(self, total):
        if type(total) is not int or not 24 <= total < 2**63:
            raise Failure('positive signed-Long model size required')
        self.total = total
        self.samples = []
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
        match = _EVENT.fullmatch(line)
        if match is None:
            return
        tick, copied, total, percent = map(int, match.groups())
        if (tick >= 2**63 or total != self.total or copied > total or
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
