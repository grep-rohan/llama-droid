"""Host-only guards for a memory command bracketed by one owned partial copy.

Callers must actually capture before -> meminfo -> after in that order, validate
fixed emulator/APK/UI/source ownership, and never synthesize observations.
"""
import re
import stat

from model_import_device import Failure


def verify_live_heap(before, memory, after, expected, total):
    def require(condition):
        if not condition:
            raise Failure('unproven live-copy heap interval')

    def integer(value, minimum=0):
        return type(value) is int and minimum <= value < 2**63

    def digest(value):
        return isinstance(value, str) and re.fullmatch('[0-9a-f]{64}', value) is not None

    try:
        require(integer(total, 65))
        process = expected['process']
        require(all(integer(process[k], 1) for k in ['pid', 'uid', 'start_ticks']))
        require(digest(expected['record_sha256']))
        owner = expected['candidate']
        require(integer(owner['inode'], 1) and digest(owner['name_sha256']) and digest(owner['prefix_sha256']))
        counts = []
        for observation in [before, after]:
            require(observation['process'] == process)
            require(all(observation[k] == expected[k] for k in ['root_identity', 'record_sha256', 'payload']))
            candidate = observation['candidate']
            require(all(candidate[k] == owner[k] for k in ['inode', 'name_sha256', 'prefix_sha256']))
            require(integer(candidate['mode']) and stat.S_ISREG(candidate['mode']))
            require(type(candidate['links']) is int and candidate['links'] == 1)
            require(integer(candidate['bytes'], 64) and candidate['bytes'] < total)
            counts.append(candidate['bytes'])
        require(counts[0] <= counts[1])
        require(type(memory['pid']) is int and memory['pid'] == process['pid'])
        for key in ['java_size_KiB', 'java_alloc_KiB', 'native_size_KiB', 'native_alloc_KiB', 'total_PSS_KiB']:
            require(integer(memory[key]))
        require(memory['java_alloc_KiB'] <= memory['java_size_KiB'])
        require(memory['native_alloc_KiB'] <= memory['native_size_KiB'])
        combined = memory['java_size_KiB']+memory['native_size_KiB']
        require(combined < 2**63)
        return dict(pid=process['pid'], uid=process['uid'], process_start_ticks=process['start_ticks'],
                    candidate_inode=owner['inode'], candidate_bytes_before=counts[0], candidate_bytes_after=counts[1],
                    java_native_heap_size_KiB=combined, total_PSS_KiB=memory['total_PSS_KiB'],
                    partial_candidate_before_and_after=True, sampled_not_peak_heap=True)
    except (KeyError, TypeError, AttributeError):
        raise Failure('incomplete live-copy heap metadata') from None
