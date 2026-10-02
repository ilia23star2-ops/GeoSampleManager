package com.example.geosamplemanager.data.merge

import com.example.geosamplemanager.data.AppDatabase
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import java.io.File

/**
 * FIX 5.9-db-merge-v2/1:
 * Модели для движка слияния БД.
 *
 * MergeEngine работает с двумя источниками:
 *   "мои" — текущая БД приложения;
 *   "их"  — содержимое выбранного .gsmbackup.
 *
 * /1 сливает только участки и наряды. Пробы, скважины, заметки,
 * фото — в /2 и /3.
 */

/** Открытая временная БД архива + путь к её файлу. */
data class TempDatabaseHandle(
    val db: AppDatabase,
    val file: File
)

/**
 * Участок из архива, который надо добавить в текущую БД.
 * Храним исходный id — чтобы после вставки получить маппинг.
 */
data class AreaToAdd(
    val theirId: Long,
    val entity: AreaEntity
)

/**
 * План слияния участков.
 *
 *  - existing: theirAreaId -> myAreaId — для совпадений по имени.
 *    При дублях у меня берётся MIN(id) (первый заведённый).
 *  - toAdd: участки без аналога у меня.
 *  - duplicatesInMine: имена, у которых в моей БД больше одной
 *    записи — для предупреждения в UI.
 */
data class AreaPlan(
    val existing: Map<Long, Long>,
    val toAdd: List<AreaToAdd>,
    val duplicatesInMine: List<String>
)

/**
 * Наряд из архива, который надо добавить в текущую БД.
 * areaId уже пересчитан на мой.
 */
data class OrderToAdd(
    val theirId: Long,
    val entity: OrderEntity
)

/**
 * План слияния нарядов.
 *
 *  - existing: theirOrderId -> myOrderId — совпадения по
 *    (area_id, order_number).
 *  - toAdd: наряды без аналога у меня.
 *  - skippedOrphans: наряды, чей area_id не сматчился — пропущены.
 */
data class OrderPlan(
    val existing: Map<Long, Long>,
    val toAdd: List<OrderToAdd>,
    val skippedOrphans: Int
)

/** Краткая статистика для UI (будет использоваться в /4). */
data class MergeStats(
    val areasAdded: Int,
    val areasMatched: Int,
    val ordersAdded: Int,
    val ordersMatched: Int,
    val ordersSkipped: Int
) {
    companion object {
        fun from(areaPlan: AreaPlan, orderPlan: OrderPlan): MergeStats =
            MergeStats(
                areasAdded = areaPlan.toAdd.size,
                areasMatched = areaPlan.existing.size,
                ordersAdded = orderPlan.toAdd.size,
                ordersMatched = orderPlan.existing.size,
                ordersSkipped = orderPlan.skippedOrphans
            )
    }
}