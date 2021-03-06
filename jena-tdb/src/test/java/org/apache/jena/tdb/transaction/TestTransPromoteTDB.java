/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.tdb.transaction ;

import org.apache.jena.sparql.core.DatasetGraph ;
import org.apache.jena.sparql.transaction.AbstractTestTransPromote ;
import org.apache.jena.tdb.TDB ;
import org.apache.jena.tdb.TDBFactory ;
import org.apache.jena.tdb.sys.SystemTDB ;
import org.apache.jena.tdb.transaction.DatasetGraphTransaction ;
import org.apache.jena.tdb.transaction.TDBTransactionException ;
import org.apache.log4j.Logger ;

/** Tests for transactions that start read and then promote to write -- TDB */
public class TestTransPromoteTDB extends AbstractTestTransPromote {
    public TestTransPromoteTDB() {
        super(getLoggers()) ;
    }

    @Override
    protected DatasetGraph create() {
        return TDBFactory.createDatasetGraph() ;
    }

    private static Logger[] getLoggers() {
        return new Logger[]{
            Logger.getLogger(SystemTDB.errlog.getName()),
            Logger.getLogger(TDB.logInfoName)
        } ;
    }

    @Override
    protected void setPromotion(boolean b) {
        DatasetGraphTransaction.promotion = b ;
    }

    @Override
    protected boolean getPromotion() {
        return DatasetGraphTransaction.promotion ;
    }

    @Override
    protected void setReadCommitted(boolean b) {
        DatasetGraphTransaction.readCommittedPromotion = b ;
    }

    @Override
    protected boolean getReadCommitted() {
        return DatasetGraphTransaction.readCommittedPromotion ;
    }

    @Override
    protected Class<TDBTransactionException> getTransactionExceptionClass() {
        return TDBTransactionException.class ;
    }
}