/*
 * Copyright (c) 2004 Persistit Corporation. All Rights Reserved.
 *
 * The Java source code is the confidential and proprietary information
 * of Persistit Corporation ("Confidential Information"). You shall
 * not disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Persistit Corporation.
 *
 * PERSISTIT CORPORATION MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE
 * SUITABILITY OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
 * A PARTICULAR PURPOSE, OR NON-INFRINGEMENT. PERSISTIT CORPORATION SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING,
 * MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 * 
 * Created on Sep 10, 2004
 */
package com.persistit.unit;

import com.persistit.test.PersistitScriptedTestCase;
import com.persistit.test.PersistitTestResult;

/**
 * Adapter that allows the TestRunner to run unit tests
 * 
 * @author peter
 *
 */
public class UnitTestRunner extends PersistitScriptedTestCase {
    boolean _done;
    String _className;
    String[] _args;


    @Override
    public void executeTest() {
        try {
            System.out.println("Running Unit Test " + _className);
            final Class cl = Class.forName(_className);
            final PersistitUnitTestCase testCase = (PersistitUnitTestCase)cl.newInstance();
            testCase.setPersistit(_persistit);
            testCase.runAllTests();
            _done = true;
        } catch (final Exception e) {
            _done = true;
            final Throwable t = e;
            // if (e.getCause() != null) t = e.getCause();
            _result = new PersistitTestResult(false, t);
        }
    }

    @Override
    protected String shortDescription() {
        return "Unit Test: " + _className;
    }

    @Override
    protected String longDescription() {
        return shortDescription();
    }

    @Override
    protected String getProgressString() {
        return _done ? "done" : "running";
    }

    @Override
    protected double getProgress() {
        return _done ? 1.0 : 0.0;
    }

    protected void done() {
        _done = true;
    }

}
