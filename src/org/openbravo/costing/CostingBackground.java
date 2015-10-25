/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.0  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2012-2015 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.costing;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.exception.NoConnectionAvailableException;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.plm.Product;
import org.openbravo.model.materialmgmt.cost.CostingRule;
import org.openbravo.model.materialmgmt.transaction.MaterialTransaction;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * @author gorkaion
 * 
 */
public class CostingBackground extends DalBaseProcess {
  private static final Logger log4j = Logger.getLogger(CostingBackground.class);
  public static final String AD_PROCESS_ID = "3F2B4AAC707B4CE7B98D2005CF7310B5";
  private ProcessLogger logger;
  private int maxTransactions = 0;
  public static final String TRANSACTION_COST_DATEACCT_INITIALIZED = "TransactionCostDateacctInitialized";

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {

    logger = bundle.getLogger();
    OBError result = new OBError();
    List<String> orgsWithRule = new ArrayList<String>();
    List<String> trxs = null;
    int counter = 0, total = 0, batch = 0;
    try {
      OBContext.setAdminMode(false);
      result.setType("Success");
      result.setTitle(OBMessageUtils.messageBD("Success"));

      // Initialize Transaction Cost Date Acct
      initializeMtransCostDateAcct();

      // Get organizations with costing rules.
      StringBuffer where = new StringBuffer();
      where.append(" as o");
      where.append(" where exists (");
      where.append("    select 1 from " + CostingRule.ENTITY_NAME + " as cr");
      where.append("    where ad_isorgincluded(o.id, cr." + CostingRule.PROPERTY_ORGANIZATION
          + ".id, " + CostingRule.PROPERTY_CLIENT + ".id) <> -1 ");
      where.append("      and cr." + CostingRule.PROPERTY_VALIDATED + " is true");
      where.append(" )");
      where.append("    and ad_isorgincluded(o.id, '" + bundle.getContext().getOrganization()
          + "', '" + bundle.getContext().getClient() + "') <> -1 ");
      OBQuery<Organization> orgQry = OBDal.getInstance().createQuery(Organization.class,
          where.toString());
      List<Organization> orgs = orgQry.list();
      if (orgs.size() == 0) {
        log4j.debug("No organizations with Costing Rule defined");
        logger.logln(OBMessageUtils.messageBD("Success"));
        bundle.setResult(result);
        return;
      }
      for (Organization org : orgs) {
        orgsWithRule.add(org.getId());
      }

      // Fix the Not Processed flag for those Transactions with Cost Not Calculated
      setNotProcessedWhenNotCalculatedTransactions(orgsWithRule);

      trxs = getTransactionsBatch(orgsWithRule);
      total = trxs.size();
      while (counter < maxTransactions) {
        batch++;
        for (String trxId : trxs) {
          counter++;
          MaterialTransaction transaction = OBDal.getInstance().get(MaterialTransaction.class,
              trxId);
          log4j.debug("Start transaction process: " + transaction.getId());
          CostingServer transactionCost = new CostingServer(transaction);
          transactionCost.process();
          log4j.debug("Transaction processed: " + counter + "/" + maxTransactions + " batch: "
              + batch);
        }
        OBDal.getInstance().getConnection(true).commit();
        OBDal.getInstance().getSession().clear();
        if (counter < maxTransactions) {
          trxs = getTransactionsBatch(orgsWithRule);
          total += trxs.size();
        }
      }

      logger.logln(OBMessageUtils.messageBD("Success"));
      bundle.setResult(result);
    } catch (OBException e) {
      OBDal.getInstance().rollbackAndClose();
      calculateCorrectTrxWhileRollback(trxs, counter, total);
      String message = OBMessageUtils.parseTranslation(bundle.getConnection(), bundle.getContext()
          .toVars(), OBContext.getOBContext().getLanguage().getLanguage(), e.getMessage());
      result.setMessage(message);
      result.setType("Error");
      log4j.error(message, e);
      logger.logln(message);
      bundle.setResult(result);
      return;
    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      calculateCorrectTrxWhileRollback(trxs, counter, total);
      result = OBMessageUtils.translateError(bundle.getConnection(), bundle.getContext().toVars(),
          OBContext.getOBContext().getLanguage().getLanguage(), e.getMessage());
      result.setType("Error");
      result.setTitle(OBMessageUtils.messageBD("Error"));
      log4j.error(result.getMessage(), e);
      logger.logln(result.getMessage());
      bundle.setResult(result);
      return;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Get Transactions with Processed flag = 'Y' and it's cost is Not Calculated and set Processed
   * flag = 'N'
   */
  private void setNotProcessedWhenNotCalculatedTransactions(List<String> orgsWithRule) {
    final StringBuilder hqlTransactions = new StringBuilder();
    hqlTransactions.append(" update " + MaterialTransaction.ENTITY_NAME + " as trx set trx."
        + MaterialTransaction.PROPERTY_ISPROCESSED + " = false ");
    hqlTransactions.append(" where trx." + MaterialTransaction.PROPERTY_ISPROCESSED + " = true");
    hqlTransactions.append("   and trx." + MaterialTransaction.PROPERTY_ISCOSTCALCULATED
        + " = false");
    hqlTransactions.append("   and trx." + MaterialTransaction.PROPERTY_COSTINGSTATUS + " <> 'S'");
    hqlTransactions.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION
        + ".id in (:orgs)");
    Query updateTransactions = OBDal.getInstance().getSession()
        .createQuery(hqlTransactions.toString());
    updateTransactions.setParameterList("orgs", orgsWithRule);
    updateTransactions.executeUpdate();

    OBDal.getInstance().flush();
  }

  @SuppressWarnings("unchecked")
  private List<String> getTransactionsBatch(List<String> orgsWithRule) {
    StringBuffer where = new StringBuffer();
    where.append(" select trx." + MaterialTransaction.PROPERTY_ID + " as id ");
    where.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    where.append(" join trx." + MaterialTransaction.PROPERTY_PRODUCT + " as p");
    where.append("\n , " + org.openbravo.model.ad.domain.List.ENTITY_NAME + " as trxtype");
    where.append("\n where trx." + MaterialTransaction.PROPERTY_ISPROCESSED + " = false");
    where.append("   and trx." + MaterialTransaction.PROPERTY_COSTINGSTATUS + " <> 'S'");
    where.append("   and p." + Product.PROPERTY_PRODUCTTYPE + " = 'I'");
    where.append("   and p." + Product.PROPERTY_STOCKED + " = true");
    where.append("   and trxtype." + CostAdjustmentUtils.propADListReference + ".id = :refid");
    where.append("   and trxtype." + CostAdjustmentUtils.propADListValue + " = trx."
        + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
    where.append("   and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " <= :now");
    where.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    where.append(" order by trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE);
    where.append(" , trxtype." + CostAdjustmentUtils.propADListPriority);
    where.append(" , trx." + MaterialTransaction.PROPERTY_MOVEMENTQUANTITY + " desc");
    where.append(" , trx." + MaterialTransaction.PROPERTY_ID);
    Query trxQry = OBDal.getInstance().getSession().createQuery(where.toString());

    trxQry.setParameter("refid", CostAdjustmentUtils.MovementTypeRefID);
    trxQry.setParameter("now", new Date());
    trxQry.setParameterList("orgs", orgsWithRule);

    if (maxTransactions == 0) {
      maxTransactions = count(orgsWithRule);
    }
    trxQry.setMaxResults(1000);
    return trxQry.list();
  }

  /**
   * Return count of transactions to be calculated
   */
  private int count(List<String> orgsWithRule) {
    StringBuffer where = new StringBuffer();
    where.append(" select count(*) ");
    where.append(" from " + MaterialTransaction.ENTITY_NAME + " as trx");
    where.append(" join trx." + MaterialTransaction.PROPERTY_PRODUCT + " as p");
    where.append("\n , " + org.openbravo.model.ad.domain.List.ENTITY_NAME + " as trxtype");
    where.append("\n where trx." + MaterialTransaction.PROPERTY_ISPROCESSED + " = false");
    where.append("   and trx." + MaterialTransaction.PROPERTY_COSTINGSTATUS + " <> 'S'");
    where.append("   and p." + Product.PROPERTY_PRODUCTTYPE + " = 'I'");
    where.append("   and p." + Product.PROPERTY_STOCKED + " = true");
    where.append("   and trxtype." + CostAdjustmentUtils.propADListReference + ".id = :refid");
    where.append("   and trxtype." + CostAdjustmentUtils.propADListValue + " = trx."
        + MaterialTransaction.PROPERTY_MOVEMENTTYPE);
    where.append("   and trx." + MaterialTransaction.PROPERTY_TRANSACTIONPROCESSDATE + " <= :now");
    where.append("   and trx." + MaterialTransaction.PROPERTY_ORGANIZATION + ".id in (:orgs)");
    Query trxQry = OBDal.getInstance().getSession().createQuery(where.toString());

    trxQry.setParameter("refid", CostAdjustmentUtils.MovementTypeRefID);
    trxQry.setParameter("now", new Date());
    trxQry.setParameterList("orgs", orgsWithRule);

    return ((Number) trxQry.uniqueResult()).intValue();
  }

  private void calculateCorrectTrxWhileRollback(List<String> trxs, int _counter, int total) {
    int correctTrx = _counter - 1;
    int counter = total - 1000;
    try {
      for (String trxId : trxs) {
        if (counter == correctTrx) {
          break;
        }
        MaterialTransaction transaction = OBDal.getInstance().get(MaterialTransaction.class, trxId);
        counter++;
        CostingServer transactionCost = new CostingServer(transaction);
        transactionCost.process();
      }
      OBDal.getInstance().getConnection(true).commit();
      OBDal.getInstance().getSession().clear();
    } catch (OBException e1) {
      OBDal.getInstance().rollbackAndClose();
    } catch (Exception e1) {
      OBDal.getInstance().rollbackAndClose();
    }
  }

  private void initializeMtransCostDateAcct() throws Exception {
    boolean transactionCostDateacctInitialized = false;
    Client client = OBDal.getInstance().get(Client.class, "0");
    Organization organization = OBDal.getInstance().get(Organization.class, "0");
    try {
      transactionCostDateacctInitialized = Preferences.getPreferenceValue(
          CostingBackground.TRANSACTION_COST_DATEACCT_INITIALIZED, false, client, organization,
          null, null, null).equals("Y");
    } catch (PropertyException e1) {
      transactionCostDateacctInitialized = false;
    }

    if (!transactionCostDateacctInitialized) {

      try {
        ConnectionProvider cp = new DalConnectionProvider();
        InitializeCostingMTransCostDateacctData.initializeCostingMTransCostDateacct(
            cp.getConnection(), cp);
        InitializeCostingMTransCostDateacctData.initializeCostingMTransCostDateacct2(
            cp.getConnection(), cp);

      } catch (ServletException e) {
        log4j
            .error("SQL error in Costing Backgroung Initializing Transaction Cost Date Acct: Exception:"
                + e);
        throw new OBException("@CODE=" + e.getCause() + "@" + e.getMessage());
      } catch (NoConnectionAvailableException e) {
        log4j.error("Connection error in query: Exception:" + e);
        throw new OBException("@CODE=NoConnectionAvailable");
      } finally {
        try {
        } catch (Exception ignore) {
        }
      }

      // Create the preference
      Preference transactionCostDateacctInitializedPreference = OBProvider.getInstance().get(
          Preference.class);
      transactionCostDateacctInitializedPreference.setClient(client);
      transactionCostDateacctInitializedPreference.setOrganization(organization);
      transactionCostDateacctInitializedPreference
          .setAttribute(CostingBackground.TRANSACTION_COST_DATEACCT_INITIALIZED);
      transactionCostDateacctInitializedPreference.setSearchKey("Y");
      transactionCostDateacctInitializedPreference.setPropertyList(false);
      OBDal.getInstance().save(transactionCostDateacctInitializedPreference);
      OBDal.getInstance().flush();
      OBDal.getInstance().getConnection(true).commit();
    }
  }
}
