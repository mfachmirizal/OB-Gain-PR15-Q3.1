/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use. this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2015 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

// == OBComboBoxItem ==
// Base Combo Box for list and selector references
isc.ClassFactory.defineClass('OBComboBoxItem', isc.ComboBoxItem);

isc.OBComboBoxItem.addProperties({

  // ** {{{ filterComplete }}} **
  //
  // Prevents validation of this item while filtering because real value is not 
  // set yet.
  // see issues #26189 and #28651
  filterComplete: function () {
    var ret;

    this.preventValidation = true;
    ret = this.Super('filterComplete', arguments);
    delete this.preventValidation;
    return ret;
  }
});