package com.flavor.huhobot;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** 可被模型调用的工具。 */
public interface Tool {

    String name();

    String description();

    ObjectNode parameters();

    /** 是否属于危险操作（manual 模式下需要审批，auto 模式下直接禁用）。 */
    boolean requiresApproval();

    ToolResult execute(ToolContext context, ObjectNode arguments) throws Exception;
}
