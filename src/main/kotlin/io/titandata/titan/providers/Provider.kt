package io.titandata.titan.providers

interface Provider {
    fun getType(): String
    fun getProperties(): Map<String, String>
    fun repositoryExists(repository: String): Boolean
    fun pull(container: String, commit: String?, remoteName: String?, tags: List<String>, metadataOnly: Boolean)
    fun push(container: String, commit: String?, remoteName: String?, tags: List<String>, metadataOnly: Boolean)
    fun commit(container: String, message: String, tags: List<String>)
    fun install(properties: Map<String, String>, verbose: Boolean)
    fun abort(container: String)
    fun status(container: String)
    fun remoteAdd(container: String, uri: String, remoteName: String?, params: Map<String, String>)
    fun remoteLog(container: String, remoteName: String?, tags: List<String>)
    fun remoteList(container: String)
    fun remoteRemove(container: String, remote: String)
    fun migrate(container: String, name: String)
    fun run(image: String, repository: String?, environments: List<String>, arguments: List<String>, disablePortMapping: Boolean)
    fun uninstall(force: Boolean, removeImages: Boolean)
    fun upgrade(force: Boolean, version: String, finalize: Boolean, path: String?)
    fun checkout(container: String, guid: String?, tags: List<String>)
    fun delete(repository: String, commit: String?, tags: List<String>)
    fun tag(repository: String, commit: String, tags: List<String>)
    fun list(context: String)
    fun log(container: String, tags: List<String>)
    fun stop(container: String)
    fun start(container: String)
    fun remove(container: String, force: Boolean)
    fun cp(container: String, driver: String, source: String, path: String)
    fun clone(uri: String, container: String?, commit: String?, params: Map<String, String>, arguments: List<String>, disablePortMapping: Boolean)
}
