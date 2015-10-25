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
 * All portions are Copyright (C) 2010-2011 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.userinterface.selector;

import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.application.JSExpressionCallout;

/**
 * 
 * @author iperdomo
 */
public class DefaultExpressionCallout extends JSExpressionCallout {

  private static final long serialVersionUID = 1L;

  @Override
  protected String getExpression(VariablesSecureApp vars) {
    String inpLastFieldChanged = vars.getStringParameter("inpLastFieldChanged");
    String expression = "";
    if ("inpdefaultExpression".equals(inpLastFieldChanged)) {
      expression = vars.getStringParameter("inpdefaultExpression");
    } else if ("inpfilterExpression".equals(inpLastFieldChanged)) {
      expression = vars.getStringParameter("inpfilterExpression");
    }
    return expression;
  }
}