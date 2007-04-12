/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.jci.compilers;

import org.apache.commons.jci.problems.CompilationProblem;
import org.eclipse.jdt.core.compiler.IProblem;

public class EclipseCompilationProblem
    implements
    CompilationProblem {

    private final IProblem problem;

    public EclipseCompilationProblem(final IProblem pProblem) {
        this.problem = pProblem;
    }

    public boolean isError() {
        return this.problem.isError();
    }

    public String getFileName() {
        return new String( this.problem.getOriginatingFileName() );
    }

    public int getStartLine() {
        return this.problem.getSourceLineNumber();
    }

    public int getStartColumn() {
        return this.problem.getSourceStart();
    }

    public int getEndLine() {
        return getStartLine();
    }

    public int getEndColumn() {
        return this.problem.getSourceEnd();
    }

    public String getMessage() {
        return this.problem.getMessage();
    }

    public String toString() {
        final StringBuffer sb = new StringBuffer();
        sb.append( getFileName() ).append( " (" );
        sb.append( getStartLine() );
        sb.append( ":" );
        sb.append( getStartColumn() );
        sb.append( ") : " );
        sb.append( getMessage() );
        return sb.toString();
    }

    public int getId() {
        return this.problem.getID();
    }

}
