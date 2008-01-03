/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.apache.jmeter.engine.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.jmeter.functions.Function;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.jorphan.reflect.ClassFinder;
import org.apache.log.Logger;

/**
 * CompoundFunction.
 * 
 */
public class CompoundVariable implements Function {
	private static final Logger log = LoggingManager.getLoggerForClass();

	private String rawParameters;

	private static FunctionParser functionParser = new FunctionParser();

	private static Map functions = new HashMap();

	private boolean hasFunction, isDynamic;

	private String permanentResults = ""; // $NON-NLS-1$

	private LinkedList compiledComponents = new LinkedList();

	static {
		try {
			final String contain = // Classnames must contain this string [.functions.]
				JMeterUtils.getProperty("classfinder.functions.contain"); // $NON-NLS-1$ 
			final String notContain = // Classnames must not contain this string [.gui.]
				JMeterUtils.getProperty("classfinder.functions.notContain"); // $NON-NLS-1$
			if (contain!=null){
				log.info("Note: Function class names must contain the string: '"+contain+"'");
			}
			if (notContain!=null){
				log.info("Note: Function class names must not contain the string: '"+notContain+"'");
			}
			List classes = ClassFinder.findClassesThatExtend(JMeterUtils.getSearchPaths(),
					new Class[] { Function.class }, true, contain, notContain);
			Iterator iter = classes.iterator();
			while (iter.hasNext()) {
				// TODO skip class init - e.g. update findClassesThatExtend() to return classes instead of strings
				Function tempFunc = (Function) Class.forName((String) iter.next()).newInstance();
				String referenceKey = tempFunc.getReferenceKey();
                functions.put(referenceKey, tempFunc.getClass());
                // Add alias for original StringFromFile name (had only one underscore)
                if (referenceKey.equals("__StringFromFile")){//$NON-NLS-1$
                    functions.put("_StringFromFile", tempFunc.getClass());//$NON-NLS-1$
                }
			}
		} catch (Exception err) {
			log.error("", err);
		}
	}

	public CompoundVariable() {
		super();
		isDynamic = true;
		hasFunction = false;
	}

	public CompoundVariable(String parameters) {
		this();
		try {
			setParameters(parameters);
		} catch (InvalidVariableException e) {
		}
	}

	public String execute() {
		if (isDynamic) {
			JMeterContext context = JMeterContextService.getContext();
			SampleResult previousResult = context.getPreviousResult();
			Sampler currentSampler = context.getCurrentSampler();
			return execute(previousResult, currentSampler);
		}
		return permanentResults; // $NON-NLS-1$
	}

	/**
	 * Allows the retrieval of the original String prior to it being compiled.
	 * 
	 * @return String
	 */
	public String getRawParameters() {
		return rawParameters;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Function#execute(SampleResult, Sampler)
	 */
	public String execute(SampleResult previousResult, Sampler currentSampler) {
		if (compiledComponents == null || compiledComponents.size() == 0) {
			return ""; // $NON-NLS-1$
		}
		boolean testDynamic = false;
		StringBuffer results = new StringBuffer();
		Iterator iter = compiledComponents.iterator();
		while (iter.hasNext()) {
			Object item = iter.next();
			if (item instanceof Function) {
				testDynamic = true;
				try {
					results.append(((Function) item).execute(previousResult, currentSampler));
				} catch (InvalidVariableException e) {
				}
			} else if (item instanceof SimpleVariable) {
				testDynamic = true;
				results.append(((SimpleVariable) item).toString());
			} else {
				results.append(item);
			}
		}
		if (!testDynamic) {
			isDynamic = false;
			permanentResults = results.toString();
		}
		return results.toString();
	}

	public CompoundVariable getFunction() {
		CompoundVariable func = new CompoundVariable();
		func.compiledComponents = (LinkedList) compiledComponents.clone();
		func.rawParameters = rawParameters;
		return func;
	}

	public List getArgumentDesc() {
		return new LinkedList();
	}

	public void clear() {
		hasFunction = false;
		compiledComponents.clear();
	}

	public void setParameters(String parameters) throws InvalidVariableException {
		this.rawParameters = parameters;
		if (parameters == null || parameters.length() == 0) {
			return;
		}

		compiledComponents = functionParser.compileString(parameters);
		if (compiledComponents.size() > 1 || !(compiledComponents.get(0) instanceof String)) {
			hasFunction = true;
		}
	}

	static Object getNamedFunction(String functionName) throws InvalidVariableException {
		if (functions.containsKey(functionName)) {
			try {
				return ((Class) functions.get(functionName)).newInstance();
			} catch (Exception e) {
				log.error("", e); // $NON-NLS-1$
				throw new InvalidVariableException();
			}
		}
		return new SimpleVariable(functionName);
	}

	public boolean hasFunction() {
		return hasFunction;
	}

	// Dummy methods needed by Function interface
	
	/**
	 * @see Function#getReferenceKey()
	 */
	public String getReferenceKey() {
		return ""; // $NON-NLS-1$
	}

	public void setParameters(Collection parameters) throws InvalidVariableException {
	}

	public int getMaxArgCount() {
		return 0;
	}

	public int getMinArgCount() {
		return 0;
	}
}
