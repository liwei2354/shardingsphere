/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.sharding.distsql.handler.query;

import org.apache.shardingsphere.distsql.handler.type.rql.rule.RuleAwareRQLExecutor;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.merge.result.impl.local.LocalDataQueryResultRow;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.props.PropertiesConverter;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.audit.ShardingAuditStrategyConfiguration;
import org.apache.shardingsphere.sharding.distsql.statement.ShowUnusedShardingAuditorsStatement;
import org.apache.shardingsphere.sharding.rule.ShardingRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map.Entry;

/**
 * Show unused sharding auditors executor.
 */
public final class ShowUnusedShardingAuditorsExecutor extends RuleAwareRQLExecutor<ShowUnusedShardingAuditorsStatement, ShardingRule> {
    
    public ShowUnusedShardingAuditorsExecutor() {
        super(ShardingRule.class);
    }
    
    @Override
    public Collection<String> getColumnNames() {
        return Arrays.asList("name", "type", "props");
    }
    
    @Override
    public Collection<LocalDataQueryResultRow> getRows(final ShardingSphereDatabase database, final ShowUnusedShardingAuditorsStatement sqlStatement, final ShardingRule rule) {
        ShardingRuleConfiguration shardingRuleConfig = rule.getConfiguration();
        Collection<String> inUsedAuditors = getUsedAuditors(shardingRuleConfig);
        Collection<LocalDataQueryResultRow> result = new LinkedList<>();
        for (Entry<String, AlgorithmConfiguration> entry : shardingRuleConfig.getAuditors().entrySet()) {
            if (!inUsedAuditors.contains(entry.getKey())) {
                result.add(new LocalDataQueryResultRow(entry.getKey(), entry.getValue().getType(), PropertiesConverter.convert(entry.getValue().getProps())));
            }
        }
        return result;
    }
    
    private Collection<String> getUsedAuditors(final ShardingRuleConfiguration shardingRuleConfig) {
        Collection<String> result = new LinkedHashSet<>();
        shardingRuleConfig.getTables().stream().filter(each -> null != each.getAuditStrategy()).forEach(each -> result.addAll(each.getAuditStrategy().getAuditorNames()));
        shardingRuleConfig.getAutoTables().stream().filter(each -> null != each.getAuditStrategy()).forEach(each -> result.addAll(each.getAuditStrategy().getAuditorNames()));
        ShardingAuditStrategyConfiguration auditStrategy = shardingRuleConfig.getDefaultAuditStrategy();
        if (null != auditStrategy && !auditStrategy.getAuditorNames().isEmpty()) {
            result.addAll(auditStrategy.getAuditorNames());
        }
        return result;
    }
    
    @Override
    public Class<ShowUnusedShardingAuditorsStatement> getType() {
        return ShowUnusedShardingAuditorsStatement.class;
    }
}
