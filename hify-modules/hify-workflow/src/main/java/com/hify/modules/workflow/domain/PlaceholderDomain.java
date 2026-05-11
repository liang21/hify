package com.hify.modules.workflow.domain;

/**
 * Workflow module - Domain layer
 *
 * 职责：
 * - Service 接口实现（核心业务逻辑）
 * - 领域对象（Workflow、Node、Edge）
 * - 工作流执行引擎（节点编排、条件分支）
 * - Repository 接口定义
 * - 事务边界（@Transactional）
 * - 禁止直接依赖 Mapper，通过 Repository 接口
 * - 禁止依赖 web 层
 */
public class PlaceholderDomain {
    // Placeholder class for domain layer
}
