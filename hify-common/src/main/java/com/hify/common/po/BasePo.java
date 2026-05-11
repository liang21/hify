package com.hify.common.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Base entity with common fields
 * All PO classes should extend this class
 */
@Data
public abstract class BasePo {

    /**
     * Primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Create time (auto-fill on insert)
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Update time (auto-fill on insert and update)
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * Logical delete flag (0=not deleted, 1=deleted)
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
