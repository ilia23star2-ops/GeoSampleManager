package com.example.geosamplemanager.data.compare

/**
 * FIX 5.9-db-compare (fix-2):
 * Иерархические модели дерева сравнения.
 * Схема: Участок → Наряд → Проба / Скважина.
 */

data class CompareSampleNode(
    val sampleNumber: String,
    /** Для конфликтов — какие поля различаются («Вес, Состояние»). */
    val note: String? = null
)

data class CompareWellNode(
    val wellNumber: String
)

data class CompareOrderNode(
    val orderNumber: String,
    val samples: List<CompareSampleNode> = emptyList(),
    val wells: List<CompareWellNode> = emptyList()
)

data class CompareAreaNode(
    val areaName: String,
    val orders: List<CompareOrderNode> = emptyList()
)

data class CompareTree(
    val areas: List<CompareAreaNode> = emptyList()
) {
    val isEmpty: Boolean get() = areas.isEmpty()
    val totalAreas: Int get() = areas.size
    val totalOrders: Int get() = areas.sumOf { it.orders.size }
    val totalSamples: Int get() = areas.sumOf { a ->
        a.orders.sumOf { it.samples.size }
    }
    val totalWells: Int get() = areas.sumOf { a ->
        a.orders.sumOf { it.wells.size }
    }
}

data class CompareStatLine(
    val areas: Int,
    val orders: Int,
    val samples: Int,
    val wells: Int
)

/**
 * FIX 5.9-db-compare (fix-2):
 * Информация о конфликте — для секции «Отличается».
 * fieldLabels уже человекочитаемые: «Вес, Состояние».
 */
data class ConflictInfo(
    val areaName: String,
    val orderNumber: String,
    val sampleNumber: String,
    val fieldLabels: String
)

data class CompareResult(
    val fileName: String,
    val inArchive: CompareTree,
    val matched: CompareTree,
    val different: CompareTree,
    val myOnly: CompareTree
) {
    fun statFor(filter: CompareFilterKey): CompareStatLine = when (filter) {
        CompareFilterKey.IN_ARCHIVE -> CompareStatLine(
            inArchive.totalAreas, inArchive.totalOrders,
            inArchive.totalSamples, inArchive.totalWells
        )
        CompareFilterKey.MATCHED -> CompareStatLine(
            matched.totalAreas, matched.totalOrders,
            matched.totalSamples, matched.totalWells
        )
        CompareFilterKey.DIFFERENT -> CompareStatLine(
            different.totalAreas, different.totalOrders,
            different.totalSamples, different.totalWells
        )
        CompareFilterKey.MY_ONLY -> CompareStatLine(
            myOnly.totalAreas, myOnly.totalOrders,
            myOnly.totalSamples, myOnly.totalWells
        )
    }

    fun treeFor(filter: CompareFilterKey): CompareTree = when (filter) {
        CompareFilterKey.IN_ARCHIVE -> inArchive
        CompareFilterKey.MATCHED -> matched
        CompareFilterKey.DIFFERENT -> different
        CompareFilterKey.MY_ONLY -> myOnly
    }
}

enum class CompareFilterKey { IN_ARCHIVE, MATCHED, DIFFERENT, MY_ONLY }