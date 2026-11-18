"""Fixed-emulator read-only event transport, with an independent remote lease."""
from model_import_device import Budget, Command, Failure
import math
import time


class EventReader:
    def __init__(self, device, *, lease_seconds=10, clock=time.monotonic, command_factory=Command):
        if len(device.prefix) != 3 or device.prefix[-2:] != ['-s', 'emulator-5554']:
            raise Failure('fixed emulator event reader required')
        if type(lease_seconds) is not int or not 5 <= lease_seconds <= 60:
            raise Failure('bounded remote event lease required')
        if device.call(['shell', 'getprop', 'ro.kernel.qemu'], Budget(5), cap=5).strip() != b'1':
            raise Failure('qemu event reader required')
        self.clock = clock
        self.live_until = clock()+lease_seconds
        self.pending = bytearray()
        self.closed = False
        self.command = command_factory(device.prefix+['shell', f'timeout -s INT {lease_seconds} uiautomator events'], limit=4*1024*1024)

    def _check(self, deadline):
        if (self.closed or type(deadline) not in [int, float] or not math.isfinite(deadline)
                or self.clock() >= min(deadline, self.live_until)):
            raise Failure('event reader closed or expired')
        if self.command.proc.poll() is not None:
            raise Failure('event observer already exited')

    def _line(self):
        end = self.pending.find(b'\n')+1
        if end > 16384 or (end == 0 and len(self.pending) > 16384):
            raise Failure('event reader line limit')
        if not end:
            return b''
        line = bytes(self.pending[:end])
        del self.pending[:end]
        return line

    def read(self, deadline):
        self._check(deadline)
        if b'\n' in self.pending:
            return self._line()
        poll_end = min(deadline, self.live_until, self.clock()+.2)
        try:
            status = self.command.run(poll_end, self.pending.extend,
                                      until=lambda: b'\n' in self.pending or len(self.pending) > 16384)
            if status is not None:
                raise Failure('event session ended during read')
        except Failure as failure:
            if str(failure) != 'transport deadline expired' or self.clock() < poll_end:
                raise
        self._check(deadline)
        return self._line()

    def close(self, deadline):
        if self.closed:
            return
        self.closed = True
        try:
            # Wait for the independent remote timeout, not just the local ADB
            # pipe. A local terminate alone is not proof the observer has exited.
            status = self.command.run(deadline, lambda _: None)
            if status != 124:
                raise Failure('unexpected event observer exit')
        finally:
            self.pending.clear()
            self.command.close(min(deadline, self.clock()+2))
