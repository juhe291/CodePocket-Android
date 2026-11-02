package com.codepocket.ide

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

class SafGateway(private val resolver: ContentResolver, private val tree: Uri) : DocumentGateway {
    override val identity:String get()=tree.toString()
    private fun uri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)
    private fun info(id: String): DocumentNode = resolver.query(uri(id), PROJECTION, null, null, null)?.use { c ->
        require(c.moveToFirst()) { "目录或文件已不存在，请重新选择工作区" }
        DocumentNode(c.getString(0), c.getString(1), c.getString(2) == Document.MIME_TYPE_DIR, c.getInt(3))
    } ?: throw IOException("文件提供方未返回文档信息")
    override val root = info(DocumentsContract.getTreeDocumentId(tree))
    init { require(root.directory) { "请选择文件夹作为工作区" } }
    override fun children(id: String): List<DocumentNode> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        return resolver.query(query, PROJECTION, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(DocumentNode(c.getString(0), c.getString(1),
                c.getString(2) == Document.MIME_TYPE_DIR, c.getInt(3))) }
        } ?: throw IOException("无法读取目录；请检查授权及文件提供方")
    }
    override fun read(id: String): String = resolver.openInputStream(uri(id))?.use { stream ->
        Workspace.utf8Reader(stream).use { Workspace.readLimited(it, Workspace.MAX_CHARS) }
    }?.also { require(!it.contains('\u0000')) { "仅支持 UTF-8 文本" } }?.removePrefix("\uFEFF")
        ?: throw IOException("无法读取文件")
    override fun readBytes(id: String): ByteArray = readBinary(id,1_000_000)
    override fun readBinary(id: String,limit:Int): ByteArray = resolver.openInputStream(uri(id))?.use { stream ->
        require(limit in 1..16_000_000) { "二进制读取上限无效" }
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(out.size() + count <= limit) { if(limit==1_000_000)"运行资源超过 1 MB" else "Git文件超过读取上限" }
            out.write(buffer, 0, count)
        }
        out.toByteArray()
    } ?: throw IOException("无法同步运行资源")
    override fun write(id: String, text: String) {
        writeBytes(id,text.toByteArray(Charsets.UTF_8))
    }
    override fun writeBytes(id: String, bytes: ByteArray) {
        require(info(id).flags and Document.FLAG_SUPPORTS_WRITE != 0) { "提供方不允许写入此文件" }
        resolver.openOutputStream(uri(id), "wt")?.use { out -> out.write(bytes); out.flush() }
            ?: throw IOException("无法打开文件进行保存")
    }
    override fun create(parent: String, name: String, directory: Boolean): DocumentNode {
        return createNode(parent,name,if(directory)Document.MIME_TYPE_DIR else "text/plain")
    }
    override fun createBinary(parent:String,name:String):DocumentNode = createNode(parent,name,"application/octet-stream")
    private fun createNode(parent:String,name:String,mime:String):DocumentNode {
        require(info(parent).flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0) { "此目录不支持新建" }
        val created = DocumentsContract.createDocument(resolver, uri(parent),
            mime, name) ?: throw IOException("提供方创建失败")
        return info(DocumentsContract.getDocumentId(created))
    }
    override fun rename(id: String, name: String): DocumentNode {
        require(info(id).flags and Document.FLAG_SUPPORTS_RENAME != 0) { "提供方不支持重命名" }
        val result = DocumentsContract.renameDocument(resolver, uri(id), name) ?: throw IOException("重命名失败")
        return info(DocumentsContract.getDocumentId(result))
    }
    override fun move(id: String, oldParent: String, newParent: String): DocumentNode {
        require(info(id).flags and Document.FLAG_SUPPORTS_MOVE != 0) { "提供方不支持安全移动，请用系统文件管理器移动后重新载入" }
        val result = DocumentsContract.moveDocument(resolver, uri(id), uri(oldParent), uri(newParent))
            ?: throw IOException("移动失败")
        return info(DocumentsContract.getDocumentId(result))
    }
    override fun delete(id: String) {
        require(info(id).flags and Document.FLAG_SUPPORTS_DELETE != 0) { "提供方不支持删除" }
        require(DocumentsContract.deleteDocument(resolver, uri(id))) { "删除失败" }
    }
    companion object { private val PROJECTION = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS) }
}
