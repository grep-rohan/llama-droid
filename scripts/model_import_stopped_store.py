"""Host-only stopped-store checks; never launches a process or deletes a file."""
import copy
import re
import stat


class StoppedStoreError(ValueError):
    pass


def stopped_record_view(before, stopped, captured, total, *, process_pids):
    """Project ONLY the captured dead candidate out of an in-memory inventory.

    Callers must still decode/hash the selection, compare its active state, and
    recheck process absence/store stability before any separately authorized launch.
    This function neither validates model bodies nor authorizes file deletion.
    """
    def require(condition):
        if not condition:
            raise StoppedStoreError('unproven stopped store; no recovery authorized')

    def regular(entry):
        require(type(entry.get('mode')) is int and stat.S_ISREG(entry['mode']))
        require(entry.get('links') == 1 and type(entry.get('inode')) is int and entry['inode'] > 0)
        require(type(entry.get('bytes')) is int and entry['bytes'] >= 0)
        require(type(entry.get('mtime')) is int)
        require(isinstance(entry.get('name_sha256'), str) and
                re.fullmatch('[0-9a-f]{64}', entry['name_sha256']) is not None)

    def entries(store, count):
        require(isinstance(store, dict) and isinstance(store.get('entries'), list))
        require(len(store['entries']) == count and all(isinstance(e, dict) for e in store['entries']))
        for entry in store['entries']:
            regular(entry)
        return store['entries']

    require(type(process_pids) is list and not process_pids)
    require(type(total) is int and 24 <= total <= 2**63-1)
    require(isinstance(captured, dict) and set(captured) == {'inode', 'bytes', 'name_sha256'})
    require(type(captured['bytes']) is int and 0 < captured['bytes'] < total)
    old, current = entries(before, 2), entries(stopped, 3)
    for key in ['parent_identity', 'models']:
        a, b = before.get(key), stopped.get(key)
        require(isinstance(a, dict) and isinstance(b, dict))
        require(type(a.get('mode')) is int and stat.S_ISDIR(a['mode']))
        require(type(a.get('inode')) is int and a['inode'] > 0)
        keys = ['mode', 'inode'] + (['links'] if key == 'models' else [])
        require(all(a.get(k) == b.get(k) for k in keys))
    require(isinstance(before.get('legacy'), dict) and stopped.get('legacy') == before['legacy'])
    kinds = [entry.get('kind') for entry in old]
    require(sorted(kinds, key=str) == ['payload', 'record'])
    require(sorted([entry.get('kind') for entry in current], key=str) == ['candidate', 'payload', 'record'])
    previous = {entry['kind']: entry for entry in old}
    observed = {entry['kind']: entry for entry in current}
    require(observed['payload'] == previous['payload'])
    record = previous['record']
    require(record.get('record') == 'selection' and 0 < record['bytes'] <= 2048)
    require(isinstance(record.get('sha256'), str) and re.fullmatch('[0-9a-f]{64}', record['sha256']) is not None)
    # Checked record replacement may legally change the record inode/mtime only.
    stable_record = lambda entry: {k: v for k, v in entry.items() if k not in ['inode', 'mtime']}
    require(stable_record(observed['record']) == stable_record(record))
    candidate = observed['candidate']
    require(all(candidate.get(k) == v for k, v in captured.items()))
    view = copy.deepcopy(stopped)
    view['entries'] = [entry for entry in view['entries'] if entry['kind'] != 'candidate']
    return view
