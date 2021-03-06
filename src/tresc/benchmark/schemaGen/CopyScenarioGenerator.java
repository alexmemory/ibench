/*
 *
 * Copyright 2016 Big Data Curation Lab, University of Toronto,
 * 		   	  	  	   				 Patricia Arocena,
 *   								 Boris Glavic,
 *  								 Renee J. Miller
 *
 * This software also contains code derived from STBenchmark as described in
 * with the permission of the authors:
 *
 * Bogdan Alexe, Wang-Chiew Tan, Yannis Velegrakis
 *
 * This code was originally described in:
 *
 * STBenchmark: Towards a Benchmark for Mapping Systems
 * Alexe, Bogdan and Tan, Wang-Chiew and Velegrakis, Yannis
 * PVLDB: Proceedings of the VLDB Endowment archive
 * 2008, vol. 1, no. 1, pp. 230-244
 *
 * The copyright of the ToxGene (included as a jar file: toxgene.jar) belongs to
 * Denilson Barbosa. The iBench distribution contains this jar file with the
 * permission of the author of ToxGene
 * (http://www.cs.toronto.edu/tox/toxgene/index.html)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package tresc.benchmark.schemaGen;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.vagabond.util.CollectionUtils;
import org.vagabond.xmlmodel.AttrDefType;
import org.vagabond.xmlmodel.MappingType;
import org.vagabond.xmlmodel.RelationType;

import tresc.benchmark.Constants.ScenarioName;
import tresc.benchmark.utils.Utils;
import vtools.dataModel.expression.Path;
import vtools.dataModel.expression.Projection;
import vtools.dataModel.expression.Query;
import vtools.dataModel.expression.SPJQuery;
import vtools.dataModel.expression.SelectClauseList;
import vtools.dataModel.expression.Variable;

//MN ENHANCED genTargetRel to pass types of attributes as argument to addRelation - 11 May 2014
//MN ENHANCED genSourceRel to pass types of attributes as argument to addRelation -11 May 2014
//MN FIXED keySize in chooseSourceRels - 11 May 2014

public class CopyScenarioGenerator extends AbstractScenarioGenerator {

	private int keySize;
	private int A;
	static Logger log = Logger.getLogger(CopyScenarioGenerator.class);
	
	//MN - added new attribute to check whether we are reusing target relation or not - 11 May 2014
	private boolean targetReuse;
	//MN
	
	@Override
	protected void initPartialMapping() {
		super.initPartialMapping();
		A = Utils.getRandomNumberAroundSomething(_generator, numOfElements, numOfElementsDeviation);
		keySize = Utils.getRandomNumberAroundSomething(_generator, primaryKeySize, primaryKeySizeDeviation);
		
		if (log.isDebugEnabled()) {log.debug("-----BEFORE-----");};
		if (log.isDebugEnabled()) {log.debug("Atomic Elements: " + A);};
		if (log.isDebugEnabled()) {log.debug("Key Size: " + keySize);};
		
		A = (A > 1) ? A : 2;
		keySize = (keySize >= A) ? A - 1 : keySize;
		// PRG FIX - DO NOT ENFORCE KEY UNLESS EXPLICITLY REQUESTED - Sep 16, 2012
		// keySize = (keySize > 0) ? keySize : 1;
		
		if (log.isDebugEnabled()) {log.debug("-----AFTER-----");};
		if (log.isDebugEnabled()) {log.debug("Atomic Elements: " + A);};
		if (log.isDebugEnabled()) {log.debug("Key Size: " + keySize);};
		
		//MN BEGIN - 11 May 2014
		targetReuse = false;
		//MN END
	}

	public CopyScenarioGenerator() {
	}

	@Override
	protected void genMappings() throws Exception {
		RelationType source = m.getSourceRels().get(0);
		RelationType target = m.getTargetRels().get(0);
		
		String[] vars = fac.getFreshVars(0, source.getAttrArray().length);
		MappingType m1 = fac.addMapping(m.getCorrs());
		fac.addForeachAtom(m1.getId(), source.getName(), vars);
		fac.addExistsAtom(m1.getId(), target.getName(), vars);	
	}

	@Override
	protected boolean chooseSourceRels() throws Exception {
		RelationType rel = getRandomRel(true);
		if (rel == null)
			return false;
		
		m.addSourceRel(rel);
		A = rel.sizeOfAttrArray();
		////keySize = rel.isSetPrimaryKey() ? rel.getPrimaryKey().sizeOfAttrArray() : 0; 
		
		//MN BEGIN - 11 May 2014
		if (keySize > 0 && !rel.isSetPrimaryKey()) {
			keySize = keySize > rel.sizeOfAttrArray() ? rel.sizeOfAttrArray() : keySize;
			fac.addPrimaryKey(rel.getName(), 
					CollectionUtils.createSequence(0, keySize), true);
		}
		else if (rel.isSetPrimaryKey())
			keySize = rel.getPrimaryKey().sizeOfAttrArray();
		//MN END
		
		return true;
	}
	
	@Override
	protected boolean chooseTargetRels() throws Exception {
		RelationType rel = getRandomRel(false);
		if (rel == null)
			return false;
		
		m.addTargetRel(rel);
		A = rel.sizeOfAttrArray();
		//MN discuss it with Patricia - 11 May 2014
		keySize = rel.isSetPrimaryKey() ? rel.getPrimaryKey().sizeOfAttrArray() : 0; 
		//MN BEGIN - 11 May 2014
		targetReuse = true;
		//MN END
		
		return true;
	}
	
	@Override
	protected void genCorrespondences () {
		RelationType source = m.getSourceRels().get(0);
		RelationType target = m.getTargetRels().get(0);
		
		for(int i = 0; i < source.sizeOfAttrArray(); i++) {
			AttrDefType sAttr = source.getAttrArray(i);
			AttrDefType tAttr = target.getAttrArray(i);
		
			fac.addCorrespondence(source.getName(), 
					sAttr.getName(), target.getName(), tAttr.getName());
		}
	}
	
	@Override
	protected void genTransformations () throws Exception {
		String creates = m.getRelName(0, false);
		Query q;
		
		q = genQuery();
		q.storeCode(q.toTrampString(m.getMapIds()));
		q = addQueryOrUnion(creates, q);
		
		fac.addTransformation(q.getStoredCode(), m.getMapIds(), creates);
	}
	
	private Query genQuery () {
		String sRelName = m.getRelName(0, true);
		int numAttrs = m.getNumRelAttr(0, true);
		
		SPJQuery query = new SPJQuery();
		SelectClauseList qselect = query.getSelect();
		Variable var = new Variable(("XL" + 0 + "V" + 0).toLowerCase());
		query.getFrom().add(var, new Projection(Path.ROOT, sRelName));
		
		for(int i = 0; i < numAttrs; i++) {
			String srcA =  m.getAttrId(0, i, true);
			String targetA  = m.getAttrId(0, i, false);
			qselect.add(targetA, new Projection(var, srcA));
		}
		
		return query;
	}
	
	
	@Override
	protected void genSourceRels() throws Exception {
		String[] attrs = new String[A];
		String relName = randomRelName(0);
		String hook = getRelHook(0);
		String[] keys = new String[keySize];
		
		//MN BEGIN - 11 May 2014
		String[] attrsType = new String[A];
		//MN END
		
		// generate the appropriate number of keys
		for (int i = 0; i < A; i++) {
			String attrName = randomAttrName(0,i);
			if (i < keySize) {
				attrName = attrName + "ke" + i;
				keys[i] = attrName;
			}
			attrs[i] = attrName;
			
			//MN BEGIN - 11 May 2014
			if(targetReuse)
				attrsType[i] = m.getTargetRels().get(0).getAttrArray(i).getDataType();
			//MN END
		}
		
		
//		for (int j = 0; j < keySize; j++)
//			keys[j] = randomAttrName(0, 0) + "ke" + j;
//		
//		int keyCount = 0;
//		for (int i = 0; i < A; i++) {
//			String attrName = randomAttrName(0, i);
//
//			if (keyCount < keySize)
//				attrName = keys[keyCount];
//			
//			keyCount++;
//			
//			attrs[i] = attrName;
//		}
		
		RelationType rel = null;
		
		//MN BEGIN - 11 May 2014
		if(!targetReuse)
			rel = fac.addRelation(hook, relName, attrs, true);
		else
			rel = fac.addRelation(hook, relName, attrs, attrsType, true);
		//MN END
		
		// PRG FIX - DO NOT ENFORCE KEY UNLESS EXPLICITLY REQUESTED - Sep 16, 2012
		if (keySize > 0)
			fac.addPrimaryKey(rel.getName(), keys, true);
		
		//MN BEGIN - 11 May 2014
		targetReuse = false;
		//MN END
	}

	@Override
	protected void genTargetRels() throws Exception {
		RelationType s = m.getSourceRels().get(0);
		String[] attrs = new String[A];
		String relName = s.getName() + "copy" + curRep + "_" + fac.getNextId("R");
		String hook = getRelHook(0);
		//MN considered an array to pass types of attributes as argument to addRelation - 4 May 2014
		List<String> attrsType = new ArrayList<String> ();
		//MN
		
		for(int i = 0; i < s.getAttrArray().length; i++){
			attrs[i] = s.getAttrArray(i).getName();
			//MN BEGIN - 4 May 2014
			attrsType.add(m.getSourceRels().get(0).getAttrArray(i).getDataType());
			//MN END
		}
		
		//MN modified - 4 May 2014
		fac.addRelation(hook, relName, attrs, attrsType.toArray(new String[] {}), false);
		//MN
		
		String[] keys = new String[keySize];
		for (int j = 0; j < keySize; j++)
			keys[j] = attrs[j];
		
		// PRG FIX - DO NOT ENFORCE KEY UNLESS EXPLICITLY REQUESTED - Sep 16, 2012
		if (keySize > 0)
			fac.addPrimaryKey(relName, keys, false);
	}

	@Override
	public ScenarioName getScenType() {
		return ScenarioName.COPY;
	}
}
