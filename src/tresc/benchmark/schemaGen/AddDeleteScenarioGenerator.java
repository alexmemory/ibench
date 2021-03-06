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
import java.util.Vector;

import org.vagabond.util.CollectionUtils;
import org.vagabond.xmlmodel.MappingType;
import org.vagabond.xmlmodel.RelationType;
import org.vagabond.xmlmodel.SKFunction;

import tresc.benchmark.Constants.MappingLanguageType;
import tresc.benchmark.Constants.ScenarioName;
import tresc.benchmark.Constants.SkolemKind;
import tresc.benchmark.utils.Utils;
import vtools.dataModel.expression.Path;
import vtools.dataModel.expression.Projection;
import vtools.dataModel.expression.Query;
import vtools.dataModel.expression.SPJQuery;
import vtools.dataModel.expression.SelectClauseList;
import vtools.dataModel.expression.Variable;

/**
 * Copies the source table and then adds a new attribute and deletes an old attribute from the target relation.
 * 
 * @author mdangelo
 */

// PRG FIXED Bogus Generation of Random Skolem Argument Sets (method generateSKs(), SkolemKind.RANDOM )- Sep 18, 2012
// PRG FIXED Infinite Loop Bug in method generateSKs(), case SkolemKind.RANDOM  - Sep 18, 2012
// PRG Systematically using "Random Without Replacement" strategy/algorithm when dealing with SkolemKind.RANDOM mode everywhere! - Sep 21, 2012

// BORIS TO DO - Revise method genQueries() as it might be out of sync now - Sep 21, 2012 

// MN ENHANCED genTargetRel to pass types of attributes as argument to addRelation in genTargetRels - 4 May 2014
// MN ENHANCED genSourceRel to pass types of attributes as argument to addRelation in genSourceRels - 11 May 2014
// MN ADDED checking whether value of keySize is greater than number of source relation attributes or not - 11 May 2014


public class AddDeleteScenarioGenerator extends AbstractScenarioGenerator 
{
	private int numOfSrcTblAttr;
	private int numAddAttr;
	private int numDelAttr;
	private int keySize;
	private SkolemKind sk;
	
	//MN - added new attribute to pass types of attributes when reusing target relation - 11 May 2014
	private boolean targetReuse;
	//MN
	
	@Override
	protected void initPartialMapping() {
		super.initPartialMapping();
		numOfSrcTblAttr = Utils.getRandomNumberAroundSomething(_generator, numOfElements, numOfElementsDeviation);
		numAddAttr = Utils.getRandomNumberAroundSomething(_generator, numNewAttr, numNewAttrDeviation);
		numDelAttr = Utils.getRandomNumberAroundSomething(_generator, numRemovedAttr, numRemovedAttrDeviation);
		keySize = Utils.getRandomNumberAroundSomething(_generator, primaryKeySize, primaryKeySizeDeviation);
		
		sk = SkolemKind.values()[typeOfSkolem];
		
		numOfSrcTblAttr = (numOfSrcTblAttr > 2) ? numOfSrcTblAttr : 2;
		
		// make sure we never delete all attributes, and that we never delete no attributes
		numDelAttr = (numDelAttr > 0) ? numDelAttr : 1;
		numDelAttr = (numDelAttr < numOfSrcTblAttr) ? numDelAttr : (numOfSrcTblAttr-1);
				
		numAddAttr = (numAddAttr > 0) ? numAddAttr : 1;
		
		// ensure that we will be able to generate a key
		keySize = (keySize <= numOfSrcTblAttr-numDelAttr) ? keySize : numOfSrcTblAttr-numDelAttr;
		
		// PRG FIX - DO NOT ENFORCE KEY UNLESS EXPLICITLY REQUESTED - Sep 16, 2012
		// PRG Added the following code to always force key generation when SkolemKind.KEY 
		if (sk == SkolemKind.KEY)
			keySize = (keySize > 0) ? keySize : 1;
		
		//MN BEGIN - 11 May 2014
		targetReuse = false;
		//MN END
	}
	
	@Override
	protected boolean chooseSourceRels () throws Exception {
		int minAttrs = keySize + numDelAttr;
		RelationType rel;
		
		// get a random relation
		rel = getRandomRel(true, minAttrs);
		
		//MN we can consider Max_Num_Tries to add more flexibility
		//MN To Do: we need to relax the value of minimum required number of attributes - 11 May 2014
		if (rel == null) 
			return false;
		
		numOfSrcTblAttr = rel.sizeOfAttrArray();
		
		// create primary key if necessary
		if (!rel.isSetPrimaryKey() && keySize > 0) {
			fac.addPrimaryKey(rel.getName(), 
					CollectionUtils.createSequence(0, keySize), true);
		}
		// adapt keySize
		else if (rel.isSetPrimaryKey()) {
			keySize = rel.getPrimaryKey().sizeOfAttrArray();
			if (rel.sizeOfAttrArray() - keySize < numDelAttr)
				numDelAttr = rel.sizeOfAttrArray() - keySize; 
		}
		
		m.addSourceRel(rel);
		
		return true;

	}
	
	@Override
	protected void genSourceRels() throws Exception {
		String srcName = randomRelName(0);
		String[] attrs = new String[numOfSrcTblAttr];
		//MN - considered an array to pass types of attributes as argument to addRelation -11 May 2014
		String[] attrsType = new String[numOfSrcTblAttr];
		
		// generate the appropriate number of keys
		String[] keys = new String[keySize];
		for (int j = 0; j < keySize; j++)
			keys[j] = randomAttrName(0, 0) + "ke" + j;

		int keyCount = 0;
		for (int i = 0; i < numOfSrcTblAttr; i++) {
			String attrName = randomAttrName(0, i);

			if (keyCount < keySize)
				attrName = keys[keyCount];
			
			keyCount++;
			
			attrs[i] = attrName;
			
			//MN BEGIN - 11 May 2014
			if(targetReuse)
				if(i<m.getTargetRels().get(0).sizeOfAttrArray()-numAddAttr)
					attrsType[i] = m.getTargetRels().get(0).getAttrArray(i).getDataType();
				else
					attrsType[i] = "TEXT";
			//MN END
		}

		//MN BEGIN - 11 May 2014
		if (!targetReuse)
			fac.addRelation(getRelHook(0), srcName, attrs, true);
		else
			fac.addRelation(getRelHook(0), srcName, attrs, attrsType, true);
		//MN END
		
		// PRG FIX - DO NOT ENFORCE KEY UNLESS EXPLICITLY REQUESTED - Sep 17, 2012
		// if (sk == SkolemKind.KEY)
		if (keySize > 0 || sk == SkolemKind.KEY)
			fac.addPrimaryKey(srcName, keys, true);

		//MN BEGIN - 11 May 2014
		targetReuse = false;
		//MN END
	}

	@Override
	protected boolean chooseTargetRels () throws Exception {
		RelationType rel;
		int minAttr = keySize + numAddAttr;
		
		rel = getRandomRel(false, minAttr);
		if (rel == null)
			return false;
			
		if (keySize > 0 && !rel.isSetPrimaryKey()) {
			fac.addPrimaryKey(rel.getName(), 
					CollectionUtils.createSequence(0, keySize), false);
		}
		else if (rel.isSetPrimaryKey()) {
			keySize = rel.getPrimaryKey().sizeOfAttrArray();
		}
		if (rel.sizeOfAttrArray() + numDelAttr - numAddAttr <= keySize)
			numDelAttr = numAddAttr;
		numOfSrcTblAttr = rel.sizeOfAttrArray() + numDelAttr - numAddAttr;
		
		m.addTargetRel(rel);
		
		//MN BEGIN - 11 May 2014
		targetReuse = true;
		//MN END
		
		return true;
	}
	
	@Override
	protected void genTargetRels() throws Exception {
		String trgName = randomRelName(0);
		String[] attrs = new String[numOfSrcTblAttr + numAddAttr - numDelAttr];
		String[] srcAttrs = m.getAttrIds(0, true);
		
		//MN considered an array to pass types of attributes of target relation as argument to addRelation
		//MN this is important, especially when reusing HP source relations as source relation
		//MN because type of one of the attributes is INT8 - 4 May 2014
		List<String> attrsType = new ArrayList<String> ();

		// copy src attrs less the amount we should be deleting
		System.arraycopy(srcAttrs, 0, attrs, 0, numOfSrcTblAttr - numDelAttr);
		//MN BEGIN - May 8
		for(int i=0; i<numOfSrcTblAttr-numDelAttr; i++)
			attrsType.add(m.getSourceRels().get(0).getAttrArray(i).getDataType());
		//MN END
		
		// create random names for the added attrs
		for (int i = (numOfSrcTblAttr - numDelAttr); i < (numOfSrcTblAttr - numDelAttr) + numAddAttr; i++){
			attrs[i] = randomAttrName(0, i);
			//MN BEGIN - 8 May 2014
			attrsType.add("TEXT");
			//MN END
		}

		//MN - 4 May 2014
		fac.addRelation(getRelHook(0), trgName, attrs, attrsType.toArray(new String[] {}), false);
		
		String[] keys = new String[keySize];
		for (int j = 0; j < keySize; j++)
			keys[j] = srcAttrs[j];
		
		// PRG FIX - DO NOT ENFORCE KEY UNLESS EXPLICITLY REQUESTED - Sep 17, 2012
		// if (sk == SkolemKind.KEY)
		if (keySize > 0 || sk == SkolemKind.KEY)
			fac.addPrimaryKey(trgName, keys, false);
	}
	
	@Override
	protected void genCorrespondences() {
		for (int i = 0; i < (numOfSrcTblAttr - numDelAttr); i++)
			addCorr(0, i, 0, i);
	}
	
	@Override
	protected void genMappings() throws Exception {
		MappingType m1 = fac.addMapping(m.getCorrs());
		
		// source table get fresh variables
		fac.addForeachAtom(m1, 0, fac.getFreshVars(0, numOfSrcTblAttr));

		switch (mapLang) {
		// target tables gets fresh vars for the new attrs
		case FOtgds:
			String[] srcVars = fac.getFreshVars(0, numOfSrcTblAttr - numDelAttr);
			String[] newVars = fac.getFreshVars(numOfSrcTblAttr, numAddAttr);
			
			String[] result = new String[srcVars.length+newVars.length];
			System.arraycopy(srcVars, 0, result, 0, srcVars.length);
			System.arraycopy(newVars, 0, result, srcVars.length, newVars.length);
			
			fac.addExistsAtom(m1, 0, result);
			break;
		// target gets all the src variables + skolem terms for the new attrs
		case SOtgds:
			fac.addEmptyExistsAtom(m1, 0);
			fac.addVarsToExistsAtom(m1, 0, fac.getFreshVars(0, numOfSrcTblAttr - numDelAttr));
			SkolemKind sk1 = sk;
			if(sk == SkolemKind.VARIABLE)
				sk1 = SkolemKind.values()[_generator.nextInt(4)];
			generateSKs(m1, sk1);
			break;
		}
	}
	
	// PRG Rewrote method generateSKs() to fixed Bogus Generation of Random Skolem Argument Sets - Sep 18, 2012
	
	/* mdangelo's Version prior Sep 18, 2012
	private void generateSKs(MappingType m1, SkolemKind sk) {
		int numArgsForSkolem = numOfSrcTblAttr;

		// if we are using a key in the original relation then we base the
		// skolem on just that key
		if (sk == SkolemKind.KEY)
			for (int i = 0; i < numAddAttr; i++)
				fac.addSKToExistsAtom(m1, 0, fac.getFreshVars(0, keySize));
		else if (sk == SkolemKind.EXCHANGED)
			for (int i = 0; i < numAddAttr; i++)
				fac.addSKToExistsAtom(m1, 0, fac.getFreshVars(0, numOfElements - numDelAttr));
		else {
			// if configuration specifies that we need to randomly decide how
			// many arguments the skolem will take, generate a random number
			if (sk == SkolemKind.RANDOM)
				numArgsForSkolem = Utils.getRandomNumberAroundSomething(_generator,
								numOfSrcTblAttr / 2, numOfSrcTblAttr / 2);

			// ensure that we are still within the bounds of the number of
			// source attributes
			if (numArgsForSkolem > numOfSrcTblAttr)
				numArgsForSkolem = numOfSrcTblAttr;

			// add all the source attributes as arguments for the skolem
			// function
			for (int i = 0; i < numAddAttr; i++)
				fac.addSKToExistsAtom(m1, 0, fac.getFreshVars(0, numArgsForSkolem));
		}
	} */
	
	// PRG Newly implemented method generateSKs() - Sep 18, 2012
	// PRG NOTE: we purposely generate a different random argument set for each newly added attribute when sk == SkolemKind.RANDOM
	// PRG Systematically using "Random Without Replacement" strategy/algorithm when dealing with SkolemKind.RANDOM mode everywhere! - Sep 21, 2012
		
	private void generateSKs(MappingType m1, SkolemKind sk) 
	{
		int numArgsForSkolem = numOfSrcTblAttr;

		if (log.isDebugEnabled()) {log.debug("ADD DELETE - Method generateSKs() with totalVars = " + numOfSrcTblAttr + " and Num of New Skolems = " + numAddAttr);};
		
		for (int j = 0; j < numAddAttr; j++) {
			
			// if we are using a key in the original relation then we base the
			// skolem on just that key
			if (sk == SkolemKind.KEY)

				fac.addSKToExistsAtom(m1, 0, fac.getFreshVars(0, keySize));
			
			else if (sk == SkolemKind.EXCHANGED) {
				
				// Use all exchanged variables to define the argument set of this Skolem
				// This mode only makes sense for ADD DELETE scenario
				fac.addSKToExistsAtom(m1, 0, fac.getFreshVars(0, numOfSrcTblAttr - numDelAttr));
			}
			else if (sk == SkolemKind.RANDOM) {
				
				// Generate a random number of args for this Skolem (Uniform distribution between 0 (inclusive) and numOfSrcTblAttr (exclusive))
				numArgsForSkolem = Utils.getRandomUniformNumber(_generator, numOfSrcTblAttr);
				// Ensure we generate at least a random argument set of size > 0
				numArgsForSkolem = (numArgsForSkolem == 0 ? numOfSrcTblAttr : numArgsForSkolem);

				// Generate a random argument set	
				Vector<String> randomArgs = Utils.getRandomWithoutReplacementSequence(_generator, numArgsForSkolem, model.getAllVarsInMapping(m1, true));
				
				fac.addSKToExistsAtom(m1, 0, Utils.convertVectorToStringArray(randomArgs));
				
				// PRG Replaced the following fragment of code as it does not guarantee convergence - Sep 21, 2012
				/* 
				int MaxRandomTries = 30;
				int attempts = 0;
				boolean ok = false;
				
				for (int i = 0; i < numArgsForSkolem; i++) {

					while (!ok & attempts++ < MaxRandomTries) {
						
						// Get random position 
						int pos = Utils.getRandomNumberAroundSomething(_generator, numOfSrcTblAttr / 2, numOfSrcTblAttr / 2);
						// Adjust random position value just in case it falls outside limits
						pos = (pos >= numOfSrcTblAttr) ? numOfSrcTblAttr - 1 : pos;
						
						// Make sure we have not already added this variable before
						// If so, attempt to get another random position up to a max of 30 tries
						if (randomArgs.indexOf(fac.getFreshVars(pos, 1)[0]) == -1) {
							randomArgs.add(fac.getFreshVars(pos, 1)[0]);
							ok = true;
						    break;
						}
						
					}
					// Plainly give up after 30 tries. If so, we may end up with an argument set with fewer variables.
				
				}
				// Make sure we were able to generate at least 1 variable from randomArgs. If not, we use all source attributes
				if (randomArgs.size() > 0) {
				
					Collections.sort(randomArgs);
					fac.addSKToExistsAtom(m1, 0, Utils.convertVectorToStringArray(randomArgs));
					
				} else  // If not, just use all source attributes for the sake of completion
					fac.addSKToExistsAtom(m1, 0, fac.getFreshVars(0, numOfSrcTblAttr));
				*/
					
			} else { // SkolemKind.ALL

				fac.addSKToExistsAtom(m1, 0, fac.getFreshVars(0, numArgsForSkolem));
			}
		}
	}
	
	@Override
	protected void genTransformations() throws Exception {
		String creates = m.getRelName(0, false);
		Query q;
		
		q = genQueries();
		q.storeCode(q.toTrampString(m.getMapIds()));
		q = addQueryOrUnion(creates, q);
		fac.addTransformation(q.getStoredCode(), m.getMapIds(), creates);
	}
	
	private Query genQueries() throws Exception {
		String sourceRelName = m.getRelName(0, true);
		String[] tAttrs = m.getAttrIds(0, false);
		MappingType m1 = m.getMaps().get(0);
		
		// create the query for the source? table
		SPJQuery q = new SPJQuery();
		q.getFrom().add(new Variable("X"),
				new Projection(Path.ROOT, sourceRelName));

		SelectClauseList sel = q.getSelect();

		// add all attribute names to the select clause (stopping at the deleted ones)
		for(int j = 0; j < numOfSrcTblAttr-numDelAttr; j++)
		{
			Projection att = new Projection(new Variable("X"), tAttrs[j]);
			sel.add(tAttrs[j], att);
		}
		
		// retrieve skolems for the new attributes from what was generated in genMappings - this is basically just a way of cloning the existing skolem
		for(int i = 0 ; i < numAddAttr; i++) {
			int attPos = i + numOfSrcTblAttr-numDelAttr;
			String attName = tAttrs[attPos];
			String skName;
			int numArgs;
			
			if(mapLang.equals(MappingLanguageType.SOtgds)) {
				SKFunction sk = m.getSkolemFromAtom(m1, false, 0, attPos);
				skName = sk.getSkname();
				numArgs = sk.sizeOfVarArray();
			}
			else {
				skName = fac.getNextId("SK");
				if (sk == SkolemKind.KEY)
					numArgs = keySize;
				else if (sk == SkolemKind.EXCHANGED)
					numArgs = numOfElements - numDelAttr;
				else { // (sk == SkolemKind.RANDOM)
					// if configuration specifies that we need to randomly decide how
					// many arguments the skolem will take, generate a random number
					
					numArgs = Utils.getRandomNumberAroundSomething(_generator,
										numOfSrcTblAttr / 2, numOfSrcTblAttr / 2);

					// ensure that we are still within the bounds of the number of
					// source attributes
					if (numArgs > numOfSrcTblAttr)
						numArgs = numOfSrcTblAttr;
				}
			}
			
			vtools.dataModel.expression.SKFunction stSK = 
					new vtools.dataModel.expression.SKFunction(skName);
			
			// this works because the key is always the first attribute 
			for(int j = 0; j < numArgs; j++) {			
				String sAttName = m.getAttrId(0, j, true);
				Projection att = new Projection(new Variable("X"), sAttName);
				stSK.addArg(att);
			}
			
			sel.add(attName, stSK);
			q.setSelect(sel);
		}

		return q;
	}

	@Override
	public ScenarioName getScenType() {
		return ScenarioName.ADDDELATTRIBUTE;
	}

}
