package io.github.greprohan.llamadroid.modelimport

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class CheckedModelRecordTest {
    private val old = ModelRecord.Selection("a".repeat(32))
    private val new = old.copy(pendingPickerNonce = "b".repeat(32))

    @Test fun explicitlyConfirmedResetRepairKeepsUnreadableBaseUntilAtomicSwitch() {
        val reset = ModelRecord.ResetAuthorization("c".repeat(32), "d".repeat(32))
        for (event in listOf("create:reset.next", "write", "flush", "sync", "rename", "renamed")) {
            val files = FakeModelFiles().apply { putBytes("reset", byteArrayOf(1)) }
            val identity = files.stat("reset")!!
            files.hook = { if (it == event) throw IOException(event) }
            CheckedModelRecord(files, ModelRecordFile.RESET).use { record ->
                val result = record.repairReset(identity, reset)
                assertEquals(if (event == "renamed") CheckedModelRecord.State.NEW else CheckedModelRecord.State.OLD, result.state)
                if (event == "renamed") assertEquals(reset, record.read())
                else assertArrayEquals(byteArrayOf(1), files.nodes.getValue("reset").bytes)
            }
            assertFalse(files.events.contains("remove:reset"))
            assertTrue(files.handles.all { it.closed })
        }
    }

    @Test fun resetRepairRefusesChangedIdentityAndNeverRepairsSelection() {
        val reset = ModelRecord.ResetAuthorization("c".repeat(32), "d".repeat(32))
        val files = FakeModelFiles().apply { putBytes("reset", byteArrayOf(1)) }
        val identity = files.stat("reset")!!
        files.putBytes("reset", byteArrayOf(2))
        assertEquals(CheckedModelRecord.State.AMBIGUOUS, CheckedModelRecord(files, ModelRecordFile.RESET).repairReset(identity, reset).state)
        assertArrayEquals(byteArrayOf(2), files.nodes.getValue("reset").bytes)
        try { CheckedModelRecord(files).repairReset(identity, reset); fail("Selection cannot use reset consent") }
        catch (_: IllegalArgumentException) { }
        assertFalse(files.events.any { it.startsWith("create:") || it.startsWith("remove:") })
    }

    @Test fun preCommitFailuresPreserveOldBytesAndStopAllWriters() {
        for (event in listOf("create:selection.next", "info-write", "write", "written", "flush", "sync", "closed-write",
                             "open:selection.next", "read:selection.next", "rename")) {
            val files = FakeModelFiles().apply { put("selection", old) }
            val bytes = files.nodes.getValue("selection").bytes.copyOf()
            val failure = IOException(event)
            files.hook = { if (it == event) throw failure }
            val result = CheckedModelRecord(files).replace(old, new)
            assertEquals(event, CheckedModelRecord.State.OLD, result.state)
            assertSame(failure, result.failure)
            assertArrayEquals(bytes, files.nodes.getValue("selection").bytes)
            assertTrue(files.handles.all { it.closed })
            assertFalse(files.events.contains("remove:selection"))
        }
    }

    @Test fun exceptionAfterRenameIsReconciledAsNewWithoutRollback() {
        val files = FakeModelFiles().apply { put("selection", old) }
        val failure = IOException("acknowledgement lost")
        files.hook = { if (it == "renamed") throw failure }
        val result = CheckedModelRecord(files).replace(old, new)
        assertEquals(CheckedModelRecord.State.NEW, result.state)
        assertSame(failure, result.failure)
        assertFalse(files.events.any { it.startsWith("remove:") })
    }

    @Test fun falseNormalRenameAndWriteReturnsCannotReportSuccess() {
        for (event in listOf("rename", "write")) {
            val files = FakeModelFiles().apply { put("selection", old); silent += event }
            val result = CheckedModelRecord(files).replace(old, new)
            assertEquals(event, CheckedModelRecord.State.OLD, result.state)
            assertEquals(old, ModelRecordCodec.decode(files.nodes.getValue("selection").bytes))
            assertFalse(files.nodes.containsKey("selection.next"))
        }
    }

    @Test fun silentCloseKeepsOwnershipAndBlocksFurtherIoUntilExplicitRetry() {
        for (event in listOf("close-write", "close-read")) {
            val files = FakeModelFiles().apply { put("selection", old); silent += event }
            val record = CheckedModelRecord(files)
            try {
                assertEquals(CheckedModelRecord.State.AMBIGUOUS, record.replace(old, new).state)
                assertFalse(record.quiescent)
                val events = files.events.size
                assertEquals(CheckedModelRecord.State.AMBIGUOUS, record.replace(old, new).state)
                assertEquals(events, files.events.size)
                assertEquals(old, ModelRecordCodec.decode(files.nodes.getValue("selection").bytes))
                assertFalse(files.events.contains("rename"))
            } finally {
                files.silent.clear()
                record.close()
                assertTrue(files.handles.all { it.closed })
            }
            assertEquals(CheckedModelRecord.State.NEW, record.replace(old, new).state)
        }
    }

    @Test fun writeFailureRemainsPrimaryWhenCloseAlsoFails() {
        val files = FakeModelFiles().apply { put("selection", old) }
        val write = IOException("write failed")
        val close = IOException("close failed")
        files.hook = { if (it == "write") throw write; if (it == "close-write") throw close }
        val record = CheckedModelRecord(files)
        try {
            val result = record.replace(old, new)
            assertEquals(CheckedModelRecord.State.AMBIGUOUS, result.state)
            assertSame(write, result.failure)
            assertSame(close, write.suppressed.single())
            assertFalse(record.quiescent)
        } finally { files.hook = {}; record.close() }
    }

    @Test fun cleanupFalseReturnLeavesExplicitDebtAndPreservesOldSelection() {
        val files = FakeModelFiles().apply { put("selection", old); silent += "remove:selection.next" }
        val failure = IOException("sync failed")
        files.hook = { if (it == "sync") throw failure }
        val record = CheckedModelRecord(files)
        val result = record.replace(old, new)
        assertEquals(CheckedModelRecord.State.OLD, result.state)
        assertSame(failure, result.failure)
        assertTrue(result.sidecarDebt)
        assertEquals(1, failure.suppressed.size)
        assertEquals(old, record.read())
        files.hook = {}; files.silent.clear()
        assertEquals(CheckedModelRecord.State.NEW, record.replace(old, new).state)
    }

    @Test fun nextIsNeverAuthoritativeAndReadsDoNotCleanIt() {
        val files = FakeModelFiles().apply { put("selection", old); put("selection.next", new) }
        val before = files.nodes.toMap()
        assertEquals(old, CheckedModelRecord(files).read())
        assertEquals(before, files.nodes)
        assertFalse(files.events.any { it.startsWith("remove:") || it.startsWith("create:") })
    }

    @Test fun unsafeBaseAndSidecarKindsAreNotOpenedOrRemoved() {
        for (name in listOf("selection", "selection.next")) {
            for (kind in ModelFileOps.Kind.entries.filter { it != ModelFileOps.Kind.REGULAR }) {
                val files = FakeModelFiles().apply { put("selection", old); put("selection.next", new) }
                files.nodes.getValue(name).kind = kind
                val result = CheckedModelRecord(files).replace(old, new)
                assertNotEquals(CheckedModelRecord.State.NEW, result.state)
                assertFalse(files.events.contains("open:$name"))
                assertFalse(files.events.any { it.startsWith("remove:") || it.startsWith("create:") })
            }
        }
    }

    @Test fun hardLinkedRecordsAreRejectedWithoutMutation() {
        for (name in listOf("selection", "selection.next")) {
            val files = FakeModelFiles().apply { put("selection", old); put("selection.next", new) }
            files.nodes.getValue(name).links = 2
            assertNotEquals(CheckedModelRecord.State.NEW, CheckedModelRecord(files).replace(old, new).state)
            assertFalse(files.events.any { it.startsWith("create:") || it.startsWith("remove:") })
        }
    }

    @Test fun firstEmptyRecordAndResetHaveSeparateAuthoritativeSlots() {
        val files = FakeModelFiles()
        assertEquals(CheckedModelRecord.State.NEW, CheckedModelRecord(files).replace(null, old).state)
        val reset = ModelRecord.ResetAuthorization("c".repeat(32), "d".repeat(32))
        val record = CheckedModelRecord(files, ModelRecordFile.RESET)
        assertEquals(CheckedModelRecord.State.NEW, record.replace(null, reset).state)
        assertEquals(reset, record.read())
        assertEquals(old, CheckedModelRecord(files).read())
        assertThrows(IllegalArgumentException::class.java) { record.replace(reset, new) }
    }

    @Test fun mismatchedExpectationDoesNotCleanOrWriteAnything() {
        val files = FakeModelFiles().apply { put("selection", old); put("selection.next", new) }
        assertEquals(CheckedModelRecord.State.AMBIGUOUS, CheckedModelRecord(files).replace(new, old).state)
        assertFalse(files.events.any { it.startsWith("create:") || it.startsWith("remove:") || it == "rename" })
    }

    @Test fun corruptAndOversizedBaseRecordsFailClosed() {
        for (bytes in listOf(byteArrayOf(1, 2), ByteArray(2049))) {
            val files = FakeModelFiles().apply { put("selection", old) }
            files.nodes.getValue("selection").bytes = bytes
            assertEquals(CheckedModelRecord.State.AMBIGUOUS, CheckedModelRecord(files).replace(old, new).state)
            assertTrue(files.handles.all { it.closed })
            assertFalse(files.events.any { it.startsWith("create:") || it.startsWith("remove:") })
            if (bytes.size > 2048) assertFalse(files.events.contains("open:selection"))
        }
    }

    @Test fun postRenameReadFailureIsAmbiguousAndNeverRollsBack() {
        val files = FakeModelFiles().apply { put("selection", old) }
        var renamed = false
        files.hook = {
            if (it == "renamed") renamed = true
            if (renamed && it == "open:selection") throw IOException("readback unavailable")
        }
        val result = CheckedModelRecord(files).replace(old, new)
        assertEquals(CheckedModelRecord.State.AMBIGUOUS, result.state)
        assertEquals(new, ModelRecordCodec.decode(files.nodes.getValue("selection").bytes))
        assertFalse(files.events.any { it.startsWith("remove:") })
    }

    @Test fun falseRenameWithWrongInodeAndLeftoverSidecarIsAmbiguous() {
        val files = FakeModelFiles().apply { put("selection", old); silent += "rename" }
        files.hook = { if (it == "rename") files.put("selection", new) }
        assertEquals(CheckedModelRecord.State.AMBIGUOUS, CheckedModelRecord(files).replace(old, new).state)
        assertTrue(files.nodes.containsKey("selection.next"))
        assertFalse(files.events.any { it.startsWith("remove:") })
    }

    @Test fun stagesAreExplicitAndOldBytesSurviveUntilRename() {
        val files = FakeModelFiles().apply { put("selection", old) }
        val original = files.nodes.getValue("selection").bytes.copyOf()
        files.hook = { if (it in listOf("create:selection.next", "write", "flush", "sync", "close-write", "rename"))
            assertArrayEquals(original, files.nodes.getValue("selection").bytes) }
        assertEquals(CheckedModelRecord.State.NEW, CheckedModelRecord(files).replace(old, new).state)
        val stages = files.events.filter { it in listOf("write", "flush", "sync", "close-write", "rename") }
        assertEquals(listOf("write", "flush", "sync", "close-write", "rename"), stages)
    }

    @Test fun readAndReadCloseFaultsNeverAuthorizeMutation() {
        for (event in listOf("stat:selection", "open:selection", "info-read", "read:selection", "close-read", "closed-read")) {
            val files = FakeModelFiles().apply { put("selection", old) }
            files.hook = { if (it == event) throw IOException(event) }
            val record = CheckedModelRecord(files)
            try {
                assertEquals(event, CheckedModelRecord.State.AMBIGUOUS, record.replace(old, new).state)
                assertFalse(files.events.any { it.startsWith("create:") || it.startsWith("remove:") || it == "rename" })
                assertEquals(old, ModelRecordCodec.decode(files.nodes.getValue("selection").bytes))
            } finally { files.hook = {}; record.close() }
            assertTrue(files.handles.all { it.closed })
        }
    }

    @Test fun staleOwnedSidecarIsRemovedBeforeExclusiveCreation() {
        val files = FakeModelFiles().apply { put("selection", old); put("selection.next", new) }
        assertEquals(CheckedModelRecord.State.NEW, CheckedModelRecord(files).replace(old, new).state)
        assertTrue(files.events.indexOf("remove:selection.next") < files.events.indexOf("create:selection.next"))
    }

    @Test fun missingBaseNeverPromotesAnInspectedSidecar() {
        val files = FakeModelFiles().apply { put("selection.next", new) }
        assertNull(CheckedModelRecord(files).read())
        assertTrue(files.nodes.containsKey("selection.next"))
        assertFalse(files.nodes.containsKey("selection"))
        assertFalse(files.events.any { it.startsWith("remove:") || it == "rename" })
    }

    @Test fun replacementPublishesNewRecordWithoutDeletingOldFirst() {
        val files = FakeModelFiles().apply { put("selection", old) }
        val result = CheckedModelRecord(files).replace(old, new)
        assertEquals(CheckedModelRecord.State.NEW, result.state)
        assertEquals(new, ModelRecordCodec.decode(files.nodes.getValue("selection").bytes))
        assertFalse(files.nodes.containsKey("selection.next"))
    }
}
