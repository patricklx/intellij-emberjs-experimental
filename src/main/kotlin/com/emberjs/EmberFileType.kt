package com.emberjs

enum class EmberFileType(val fileExtension: String = "js") {
    ADAPTER(),
    COMPONENT(),
    CONTROLLER(),
    HELPER(),
    MODIFIER(),
    INITIALIZER(),
    MIXIN(),
    MODEL(),
    ROUTE(),
    SERIALIZER(),
    SERVICE(),
    TEMPLATE("hbs"),
    TRANSFORM(),
    VIEW();

    val fileName = "${name.lowercase()}.$fileExtension"
    val folderName = "${name.lowercase()}s"

    companion object {
        val FILE_NAMES = values().associateBy({ it.fileName }, { it })
        val FOLDER_NAMES = values().associateBy({ it.folderName }, { it })
    }
}
