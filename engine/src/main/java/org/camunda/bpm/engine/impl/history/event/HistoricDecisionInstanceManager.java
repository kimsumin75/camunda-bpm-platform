/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.impl.history.event;

import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.history.HistoricDecisionInputInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricDecisionOutputInstance;
import org.camunda.bpm.engine.history.CleanableHistoricDecisionInstanceReportResult;
import org.camunda.bpm.engine.impl.CleanableHistoricDecisionInstanceReportImpl;
import org.camunda.bpm.engine.impl.Direction;
import org.camunda.bpm.engine.impl.HistoricDecisionInstanceQueryImpl;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.camunda.bpm.engine.impl.QueryPropertyImpl;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.persistence.AbstractHistoricManager;
import org.camunda.bpm.engine.impl.persistence.entity.ByteArrayEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.impl.variable.serializer.AbstractTypedValueSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data base operations for {@link HistoricDecisionInstanceEntity}.
 *
 * @author Philipp Ossler
 */
public class HistoricDecisionInstanceManager extends AbstractHistoricManager {

  public void deleteHistoricDecisionInstancesByDecisionDefinitionId(String decisionDefinitionId) {
    if (isHistoryEnabled()) {
      List<HistoricDecisionInstanceEntity> decisionInstances = findHistoricDecisionInstancesByDecisionDefinitionId(decisionDefinitionId);

      Set<String> decisionInstanceIds = new HashSet<String>();
      for(HistoricDecisionInstanceEntity decisionInstance : decisionInstances) {
        decisionInstanceIds.add(decisionInstance.getId());
        // delete decision instance
        decisionInstance.delete();
      }

      if(!decisionInstanceIds.isEmpty()) {
        deleteHistoricDecisionInputInstancesByDecisionInstanceIds(decisionInstanceIds);

        deleteHistoricDecisionOutputInstancesByDecisionInstanceIds(decisionInstanceIds);
      }
    }
  }

  @SuppressWarnings("unchecked")
  protected List<HistoricDecisionInstanceEntity> findHistoricDecisionInstancesByDecisionDefinitionId(String decisionDefinitionId) {
    return getDbEntityManager().selectList("selectHistoricDecisionInstancesByDecisionDefinitionId", configureParameterizedQuery(decisionDefinitionId));
  }

  protected void deleteHistoricDecisionInputInstancesByDecisionInstanceIds(Set<String> decisionInstanceIds) {
    List<HistoricDecisionInputInstanceEntity> decisionInputInstances = findHistoricDecisionInputInstancesByDecisionInstanceIds(decisionInstanceIds);
    for (HistoricDecisionInputInstanceEntity decisionInputInstance : decisionInputInstances) {
      // delete input instance and byte array value if exists
      decisionInputInstance.delete();
    }
  }

  protected void deleteHistoricDecisionOutputInstancesByDecisionInstanceIds(Set<String> decisionInstanceIds) {
    List<HistoricDecisionOutputInstanceEntity> decisionOutputInstances = findHistoricDecisionOutputInstancesByDecisionInstanceIds(decisionInstanceIds);
    for (HistoricDecisionOutputInstanceEntity decisionOutputInstance : decisionOutputInstances) {
      // delete output instance and byte array value if exists
      decisionOutputInstance.delete();
    }
  }

  public void deleteHistoricHistoricInstanceByInstanceId(String historicDecisionInstanceId) {
    HistoricDecisionInstanceEntity decisionInstance = findHistoricDecisionInstance(historicDecisionInstanceId);
    boolean foundHistoricDecisionInstance = decisionInstance != null;
    if (foundHistoricDecisionInstance) {
      decisionInstance.delete();
      deleteHistoricDecisionInputAndOutputInstances(historicDecisionInstanceId);
    }
  }

  public void deleteHistoricDecisionInstanceByIds(List<String> decisionInstanceIds) {
    getDbEntityManager().deletePreserveOrder(ByteArrayEntity.class, "deleteHistoricDecisionInputInstanceByteArraysByDecisionInstanceIds", decisionInstanceIds);
    getDbEntityManager().deletePreserveOrder(ByteArrayEntity.class, "deleteHistoricDecisionOutputInstanceByteArraysByDecisionInstanceIds", decisionInstanceIds);
    getDbEntityManager().deletePreserveOrder(HistoricDecisionInputInstanceEntity.class, "deleteHistoricDecisionInputInstanceByDecisionInstanceIds", decisionInstanceIds);
    getDbEntityManager().deletePreserveOrder(HistoricDecisionOutputInstanceEntity.class, "deleteHistoricDecisionOutputInstanceByDecisionInstanceIds", decisionInstanceIds);
    getDbEntityManager().deletePreserveOrder(HistoricDecisionInstanceEntity.class, "deleteHistoricDecisionInstanceByIds", decisionInstanceIds);
  }

  protected void deleteHistoricDecisionInputAndOutputInstances(String historicDecisionInstanceId) {
    Set<String> decisionInstanceIds = new HashSet<String>();
    decisionInstanceIds.add(historicDecisionInstanceId);
    deleteHistoricDecisionInputInstancesByDecisionInstanceIds(decisionInstanceIds);
    deleteHistoricDecisionOutputInstancesByDecisionInstanceIds(decisionInstanceIds);
  }

  public void insertHistoricDecisionInstances(HistoricDecisionEvaluationEvent event) {
    if (isHistoryEnabled()) {

      HistoricDecisionInstanceEntity rootHistoricDecisionInstance = event.getRootHistoricDecisionInstance();
      insertHistoricDecisionInstance(rootHistoricDecisionInstance);

      for (HistoricDecisionInstanceEntity requiredHistoricDecisionInstances : event.getRequiredHistoricDecisionInstances()) {
        requiredHistoricDecisionInstances.setRootDecisionInstanceId(rootHistoricDecisionInstance.getId());

        insertHistoricDecisionInstance(requiredHistoricDecisionInstances);
      }
    }
  }

  protected void insertHistoricDecisionInstance(HistoricDecisionInstanceEntity historicDecisionInstance) {
    getDbEntityManager().insert(historicDecisionInstance);

    insertHistoricDecisionInputInstances(historicDecisionInstance.getInputs(), historicDecisionInstance.getId());
    insertHistoricDecisionOutputInstances(historicDecisionInstance.getOutputs(), historicDecisionInstance.getId());
  }

  protected void insertHistoricDecisionInputInstances(List<HistoricDecisionInputInstance> inputs, String decisionInstanceId) {
    for (HistoricDecisionInputInstance input : inputs) {
      HistoricDecisionInputInstanceEntity inputEntity = (HistoricDecisionInputInstanceEntity) input;
      inputEntity.setDecisionInstanceId(decisionInstanceId);

      getDbEntityManager().insert(inputEntity);
    }
  }

  protected void insertHistoricDecisionOutputInstances(List<HistoricDecisionOutputInstance> outputs, String decisionInstanceId) {
    for (HistoricDecisionOutputInstance output : outputs) {
      HistoricDecisionOutputInstanceEntity outputEntity = (HistoricDecisionOutputInstanceEntity) output;
      outputEntity.setDecisionInstanceId(decisionInstanceId);

      getDbEntityManager().insert(outputEntity);
    }
  }

 public List<HistoricDecisionInstance> findHistoricDecisionInstancesByQueryCriteria(HistoricDecisionInstanceQueryImpl query, Page page) {
    if (isHistoryEnabled()) {
      configureQuery(query);

      @SuppressWarnings("unchecked")
      List<HistoricDecisionInstance> decisionInstances = getDbEntityManager().selectList("selectHistoricDecisionInstancesByQueryCriteria", query, page);

      Map<String, HistoricDecisionInstanceEntity> decisionInstancesById = new HashMap<String, HistoricDecisionInstanceEntity>();
      for(HistoricDecisionInstance decisionInstance : decisionInstances) {
        decisionInstancesById.put(decisionInstance.getId(), (HistoricDecisionInstanceEntity) decisionInstance);
      }

      if (!decisionInstances.isEmpty() && query.isIncludeInput()) {
        appendHistoricDecisionInputInstances(decisionInstancesById, query);
      }

      if(!decisionInstances.isEmpty() && query.isIncludeOutputs()) {
        appendHistoricDecisionOutputInstances(decisionInstancesById, query);
      }

      return decisionInstances;
    } else {
      return Collections.emptyList();
    }
  }

  @SuppressWarnings("unchecked")
  public List<String> findHistoricDecisionInstanceIdsForCleanup(Integer batchSize) {
    ListQueryParameterObject parameterObject = new ListQueryParameterObject();
    parameterObject.setParameter(ClockUtil.getCurrentTime());
    parameterObject.getOrderingProperties().add(new QueryOrderingProperty(new QueryPropertyImpl("EVAL_TIME_"), Direction.ASCENDING));
    parameterObject.setFirstResult(0);
    parameterObject.setMaxResults(batchSize);
    return (List<String>) getDbEntityManager().selectList("selectHistoricDecisionInstanceIdsForCleanup", parameterObject);
  }

  protected void appendHistoricDecisionInputInstances(Map<String, HistoricDecisionInstanceEntity> decisionInstancesById, HistoricDecisionInstanceQueryImpl query) {
    List<HistoricDecisionInputInstanceEntity> decisionInputInstances = findHistoricDecisionInputInstancesByDecisionInstanceIds(decisionInstancesById.keySet());
    initializeInputInstances(decisionInstancesById.values());

    for (HistoricDecisionInputInstanceEntity decisionInputInstance : decisionInputInstances) {

      HistoricDecisionInstanceEntity historicDecisionInstance = decisionInstancesById.get(decisionInputInstance.getDecisionInstanceId());
      historicDecisionInstance.addInput(decisionInputInstance);

      // do not fetch values for byte arrays eagerly (unless requested by the user)
      if (!isBinaryValue(decisionInputInstance) || query.isByteArrayFetchingEnabled()) {
        fetchVariableValue(decisionInputInstance, query.isCustomObjectDeserializationEnabled());
      }
    }
  }

  protected void initializeInputInstances(Collection<HistoricDecisionInstanceEntity> decisionInstances) {
    for (HistoricDecisionInstanceEntity decisionInstance : decisionInstances) {
      decisionInstance.setInputs(new ArrayList<HistoricDecisionInputInstance>());
    }
  }

  @SuppressWarnings("unchecked")
  protected List<HistoricDecisionInputInstanceEntity> findHistoricDecisionInputInstancesByDecisionInstanceIds(Set<String> historicDecisionInstanceKeys) {
    return getDbEntityManager().selectList("selectHistoricDecisionInputInstancesByDecisionInstanceIds", historicDecisionInstanceKeys);
  }

  protected boolean isBinaryValue(HistoricDecisionInputInstance decisionInputInstance) {
    return AbstractTypedValueSerializer.BINARY_VALUE_TYPES.contains(decisionInputInstance.getTypeName());
  }

  protected void fetchVariableValue(HistoricDecisionInputInstanceEntity decisionInputInstance, boolean isCustomObjectDeserializationEnabled) {
    try {
      decisionInputInstance.getTypedValue(isCustomObjectDeserializationEnabled);
    } catch(Exception t) {
      // do not fail if one of the variables fails to load
      LOG.failedTofetchVariableValue(t);
    }
  }


  protected void appendHistoricDecisionOutputInstances(Map<String, HistoricDecisionInstanceEntity> decisionInstancesById, HistoricDecisionInstanceQueryImpl query) {
    List<HistoricDecisionOutputInstanceEntity> decisionOutputInstances = findHistoricDecisionOutputInstancesByDecisionInstanceIds(decisionInstancesById.keySet());
    initializeOutputInstances(decisionInstancesById.values());

    for (HistoricDecisionOutputInstanceEntity decisionOutputInstance : decisionOutputInstances) {

      HistoricDecisionInstanceEntity historicDecisionInstance = decisionInstancesById.get(decisionOutputInstance.getDecisionInstanceId());
      historicDecisionInstance.addOutput(decisionOutputInstance);

      // do not fetch values for byte arrays eagerly (unless requested by the user)
      if(!isBinaryValue(decisionOutputInstance) || query.isByteArrayFetchingEnabled()) {
        fetchVariableValue(decisionOutputInstance, query.isCustomObjectDeserializationEnabled());
      }
    }
  }

  protected void initializeOutputInstances(Collection<HistoricDecisionInstanceEntity> decisionInstances) {
    for (HistoricDecisionInstanceEntity decisionInstance : decisionInstances) {
      decisionInstance.setOutputs(new ArrayList<HistoricDecisionOutputInstance>());
    }
  }

  @SuppressWarnings("unchecked")
  protected List<HistoricDecisionOutputInstanceEntity> findHistoricDecisionOutputInstancesByDecisionInstanceIds(Set<String> decisionInstanceKeys) {
    return getDbEntityManager().selectList("selectHistoricDecisionOutputInstancesByDecisionInstanceIds", decisionInstanceKeys);
  }

  protected boolean isBinaryValue(HistoricDecisionOutputInstance decisionOutputInstance) {
    return AbstractTypedValueSerializer.BINARY_VALUE_TYPES.contains(decisionOutputInstance.getTypeName());
  }

  protected void fetchVariableValue(HistoricDecisionOutputInstanceEntity decisionOutputInstance, boolean isCustomObjectDeserializationEnabled) {
    try {
      decisionOutputInstance.getTypedValue(isCustomObjectDeserializationEnabled);
    } catch(Exception t) {
      // do not fail if one of the variables fails to load
      LOG.failedTofetchVariableValue(t);
    }
  }

  public HistoricDecisionInstanceEntity findHistoricDecisionInstance(String historicDecisionInstanceId) {
    if (isHistoryEnabled()) {
      return (HistoricDecisionInstanceEntity) getDbEntityManager().selectOne(
          "selectHistoricDecisionInstanceByDecisionInstanceId", configureParameterizedQuery(historicDecisionInstanceId));
    }
    return null;
  }

  public long findHistoricDecisionInstanceCountByQueryCriteria(HistoricDecisionInstanceQueryImpl query) {
    if (isHistoryEnabled()) {
      configureQuery(query);
      return (Long) getDbEntityManager().selectOne("selectHistoricDecisionInstanceCountByQueryCriteria", query);
    } else {
      return 0;
    }
  }

  @SuppressWarnings("unchecked")
  public List<HistoricDecisionInstance> findHistoricDecisionInstancesByNativeQuery(Map<String, Object> parameterMap, int firstResult, int maxResults) {
    return getDbEntityManager().selectListWithRawParameter("selectHistoricDecisionInstancesByNativeQuery", parameterMap, firstResult, maxResults);
  }

  public long findHistoricDecisionInstanceCountByNativeQuery(Map<String, Object> parameterMap) {
    return (Long) getDbEntityManager().selectOne("selectHistoricDecisionInstanceCountByNativeQuery", parameterMap);
  }

  protected void configureQuery(HistoricDecisionInstanceQueryImpl query) {
    getAuthorizationManager().configureHistoricDecisionInstanceQuery(query);
    getTenantManager().configureQuery(query);
  }

  protected ListQueryParameterObject configureParameterizedQuery(Object parameter) {
    return getTenantManager().configureQuery(parameter);
  }

  @SuppressWarnings("unchecked")
  public List<CleanableHistoricDecisionInstanceReportResult> findCleanableHistoricDecisionInstancesReportByCriteria( CleanableHistoricDecisionInstanceReportImpl query, Page page) {
    query.setCurrentTimestamp(ClockUtil.getCurrentTime());
    getAuthorizationManager().configureQueryHistoricFinishedInstanceReport(query, Resources.DECISION_DEFINITION);
    getTenantManager().configureQuery(query);
    return getDbEntityManager().selectList("selectFinishedDecisionInstancesReportEntities", query, page);
  }

  public long findCleanableHistoricDecisionInstancesReportCountByCriteria(CleanableHistoricDecisionInstanceReportImpl query) {
    query.setCurrentTimestamp(ClockUtil.getCurrentTime());
    getAuthorizationManager().configureQueryHistoricFinishedInstanceReport(query, Resources.DECISION_DEFINITION);
    getTenantManager().configureQuery(query);
    return (Long) getDbEntityManager().selectOne("selectFinishedDecisionInstancesReportEntitiesCount", query);
  }

}
