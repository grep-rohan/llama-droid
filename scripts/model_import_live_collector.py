"""One-shot observation orchestration; never selects a device or sends app input.

The reader must belong to one fresh event session. Every callback must honor the
absolute deadline supplied to it. A successful return is partial-copy evidence,
not a committed import, peak heap or MI-21 acceptance result.
"""
import copy
import math
import time

from model_import_device import Failure
from model_import_live_heap import verify_live_heap
from model_import_progress_events import ProgressEvents


class LiveCollector:
    def __init__(self, read_events, close_events, total, deadline, *, clock=time.monotonic):
        now = clock()
        if (type(deadline) not in [int, float] or not math.isfinite(deadline)
                or not 0 < deadline-now <= 90 or not callable(read_events) or not callable(close_events)):
            raise Failure('bounded collector callbacks/deadline required')
        self._events = ProgressEvents(total)
        self._armed = self._arm_attempted = self._used = False
        self.read_events = read_events
        self.close_events = close_events
        self.total = total
        self.deadline = deadline
        self.clock = clock
        self.closed = False

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def _check(self):
        if self.closed or self.clock() >= self.deadline:
            raise Failure('collector closed or deadline expired')

    def _call(self, callback):
        self._check()
        try:
            result = callback(self.deadline)
        except Failure:
            raise
        except Exception:
            raise Failure('collector callback failed; details suppressed') from None
        self._check()
        return result

    def _pump(self):
        chunk = self._call(self.read_events)
        if chunk is None:
            self._events.finish()
            raise Failure('event session ended before collector completion')
        if type(chunk) is not bytes or len(chunk) > 16384:
            raise Failure('bounded byte event chunk required')
        self._events.feed(chunk)

    def close(self):
        if self.closed:
            return
        self.closed = True
        try:
            self.close_events(self.deadline+2)
            if self.clock() > self.deadline+2:
                raise Failure('collector teardown exceeded grace')
        except Exception:
            raise Failure('collector teardown failed; details suppressed') from None

    def arm(self):
        if self._arm_attempted or self.closed:
            raise Failure('collector listener is one-shot')
        self._arm_attempted = True
        try:
            for _ in range(128):
                self._pump()
                if self._events.listener_event_seen:
                    self._armed = True
                    return
            raise Failure('fresh collector listener not observed within poll bound')
        finally:
            if not self._armed:
                self.close()

    def collect(self, snapshot, meminfo, expected, *, start_event_uptime_ms):
        if not self._armed or self._used or self.closed:
            raise Failure('fresh armed collector required; collection is one-shot')
        self._used = True
        try:
            if (not callable(snapshot) or not callable(meminfo) or not isinstance(expected, dict)
                    or type(start_event_uptime_ms) is not int or not 0 < start_event_uptime_ms < 2**63):
                raise Failure('copy baseline, event cutoff and bounded callbacks required')
            expected = copy.deepcopy(expected)
            # Discard arm-phase numeric feedback. The same live reader is retained;
            # no past probe/parser readiness is accepted as an arm capability.
            self._events = ProgressEvents(self.total, not_before_uptime_ms=start_event_uptime_ms)
            heaps = []
            for _ in range(32):
                self._pump()
                before = self._call(snapshot)
                if before is None:
                    raise Failure('owned partial candidate no longer observed')
                memory = self._call(meminfo)
                after = self._call(snapshot)
                if after is None:
                    raise Failure('copy interval ended during memory; sample rejected')
                sample = verify_live_heap(before, memory, after, expected, self.total)
                if heaps and sample['candidate_bytes_before'] < heaps[-1]['candidate_bytes_after']:
                    raise Failure('candidate counter decreased between heap intervals')
                heaps.append(sample)
                progress = [p for p in self._events.samples if 0 < p['bytes'] < self.total]
                if (len(heaps) >= 3 and len(progress) >= 3
                        and heaps[0]['candidate_bytes_before'] < 2**31 < heaps[-1]['candidate_bytes_after']
                        and progress[0]['bytes'] < 2**31 < progress[-1]['bytes']
                        and progress[-1]['bytes'] <= heaps[-1]['candidate_bytes_after']):
                    self._events.finish()
                    return dict(status='OBSERVED_PARTIAL_COPY_ONLY', progress_samples=copy.deepcopy(progress),
                                heap_samples=heaps, sampled_not_peak_heap=True, committed_import_proven=False)
            raise Failure('insufficient live progress/heap within sample bound')
        finally:
            self.close()
