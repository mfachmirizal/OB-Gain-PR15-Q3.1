/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2014 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.modulescript;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;

import org.openbravo.database.ConnectionProvider;

/*
 * Related to issue #27089: Trunc Invoice Date and Accounting Date
 */
public class TruncInvoiceDate extends ModuleScript {
	
  private static final Logger log4j = Logger.getLogger(TruncInvoiceDate.class);
	
  @Override
  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();
      boolean isModuleScriptExecuted= TruncInvoiceDateData.isModuleScriptExecuted(cp);
      if (!isModuleScriptExecuted){
        int count = TruncInvoiceDateData.updateDateInvoicedAndDateAcct(cp);
        if (count > 0) {
          log4j.info("Fixed " + count + " invoices.");
        }
        TruncInvoiceDateData.createPreference(cp);
      }
    } catch (Exception e) {
      handleError(e);
    }
  }
}
