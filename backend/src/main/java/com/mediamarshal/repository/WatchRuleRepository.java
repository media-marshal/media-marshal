package com.mediamarshal.repository;

import com.mediamarshal.model.entity.WatchRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WatchRuleRepository extends JpaRepository<WatchRule, Long> {

    /** 查询所有启用的规则（FileDiscoveryService 启动时调用） */
    List<WatchRule> findByEnabledTrue();

    /** 根据源目录查询规则（文件事件触发时，用于找到对应规则） */
    List<WatchRule> findBySourceDirAndEnabledTrue(String sourceDir);
}
