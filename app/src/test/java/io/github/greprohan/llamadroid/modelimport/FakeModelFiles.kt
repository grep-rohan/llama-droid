package io.github.greprohan.llamadroid.modelimport

import java.io.ByteArrayInputStream

internal class FakeModelFiles : ModelFileOps {
    data class Node(var bytes: ByteArray, val inode: Long,
                    var kind: ModelFileOps.Kind = ModelFileOps.Kind.REGULAR, var links: Long = 1)
    val nodes = mutableMapOf<String, Node>()
    val events = mutableListOf<String>()
    val silent = mutableSetOf<String>()
    val handles = mutableListOf<ModelFileOps.Handle>()
    var hook: (String) -> Unit = {}
    private var nextInode = 1L
    fun step(event: String): Boolean { events += event; hook(event); return event !in silent }
    private fun info(node: Node) = ModelFileOps.Entry(1, node.inode, node.bytes.size.toLong(), node.kind, node.links)
    fun put(name: String, record: ModelRecord) { putBytes(name, ModelRecordCodec.encode(record)) }
    fun putBytes(name: String, bytes: ByteArray) { nodes[name] = Node(bytes.copyOf(), nextInode++) }
    override fun stat(name: String): ModelFileOps.Entry? {
        step("stat:$name")
        return nodes[name]?.let { info(it) }
    }
    override fun openRead(name: String): ModelFileOps.Reader {
        step("open:$name")
        val node = nodes.getValue(name)
        return object : ModelFileOps.Reader {
            override val info get(): ModelFileOps.Entry { step("info-read"); return info(node) }
            override val input = object : ByteArrayInputStream(node.bytes) {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    step("read:$name")
                    return super.read(bytes, offset, length)
                }
            }
            override var closed = false
            override fun close() {
                if (closed) return
                if (step("close-read")) closed = true
                step("closed-read")
            }
        }.also { handles += it }
    }
    override fun createExclusive(name: String): ModelFileOps.Writer {
        step("create:$name")
        check(name !in nodes)
        val node = Node(byteArrayOf(), nextInode++)
        nodes[name] = node
        return object : ModelFileOps.Writer {
            override val info get(): ModelFileOps.Entry { step("info-write"); return info(node) }
            override var closed = false
            override fun write(bytes: ByteArray) {
                check(!closed)
                if (step("write")) node.bytes = bytes.copyOf()
                step("written")
            }
            override fun flush() { check(!closed); step("flush") }
            override fun sync() { check(!closed); step("sync") }
            override fun close() {
                if (closed) return
                if (step("close-write")) closed = true
                step("closed-write")
            }
        }.also { handles += it }
    }
    override fun rename(from: String, to: String) {
        if (step("rename")) {
            nodes[to] = nodes.remove(from) ?: error("Missing source")
            step("renamed")
        }
    }
    override fun remove(name: String) {
        if (step("remove:$name")) check(nodes.remove(name) != null)
    }
}

internal class FakeModelRoot(override val key: String = java.util.UUID.randomUUID().toString(),
                             override val files: FakeModelFiles = FakeModelFiles()) : ModelRoot {
    var valid = true
    val inventories = mutableListOf<ModelRoot.Inventory>()
    override fun prepare() { files.step("root-prepare"); validate() }
    override fun validate() { files.step("root-validate"); check(valid) }
    override fun payloadPath(name: String): String {
        validate(); require(ModelOwnership.isPayload(name))
        return "/isolated/$key/models/$name"
    }
    override fun entries(): ModelRoot.Inventory {
        validate(); files.step("list-open")
        val names = files.nodes.keys.toList() // Bounded fake fixtures, not the production iterator.
        return object : ModelRoot.Inventory {
            override var closed = false
            override fun iterator(): Iterator<String> = names.iterator()
            override fun close() {
                if (!closed && files.step("list-close")) closed = true
            }
        }.also { inventories += it }
    }
}
