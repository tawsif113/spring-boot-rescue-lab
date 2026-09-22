package com.tawsif.rescuelab.order;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    /**
     * Kept only so INC-001 can reproduce the original N+1 failure in a
     * regression test. Production reads use the two-phase methods below.
     */
    @Deprecated(forRemoval = false)
    Page<PurchaseOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(
            value = "select o.id from PurchaseOrder o order by o.createdAt desc, o.id desc",
            countQuery = "select count(o) from PurchaseOrder o"
    )
    Page<UUID> findPageIds(Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select distinct o from PurchaseOrder o where o.id in :ids")
    List<PurchaseOrder> findAllWithItemsAndProductsByIdIn(@Param("ids") Collection<UUID> ids);
}
