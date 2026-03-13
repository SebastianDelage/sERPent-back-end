package com.empresa.serpent.inventory.web;

import com.empresa.serpent.inventory.service.InventoryStockSnapshotService;
import com.empresa.serpent.inventory.web.dto.response.InventoryReconciliationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Profile("dev")
public class InventorySnapshotDevController {

    private final InventoryStockSnapshotService inventoryStockSnapshotService;

    @PostMapping("/api/dev/inventory/snapshot/rebuild")
    public ResponseEntity<Map<String, String>> rebuildSnapshots() {
        inventoryStockSnapshotService.rebuildSnapshots();

        return ResponseEntity.ok(Map.of(
                "message", "Inventory snapshots rebuilt successfully"
        ));
    }

    @GetMapping("/api/dev/inventory/snapshot/reconcile")
    public List<InventoryReconciliationResponse> reconcileSnapshots() {
        return inventoryStockSnapshotService.reconcileSnapshots();
    }

    @GetMapping("/api/dev/inventory/snapshot/inconsistencies")
    public List<InventoryReconciliationResponse> findInconsistencies() {
        return inventoryStockSnapshotService.findSnapshotInconsistencies();
    }
}