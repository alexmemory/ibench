package tresc.benchmark.schemaGen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
import org.vagabond.benchmark.model.TrampModelFactory;
import org.vagabond.benchmark.model.TrampXMLModel;
import org.vagabond.xmlmodel.AttrDefType;
import org.vagabond.xmlmodel.CorrespondenceType;
import org.vagabond.xmlmodel.RelationType;

import smark.support.MappingScenario;
import smark.support.PartialMapping;
import tresc.benchmark.Configuration;
import tresc.benchmark.Constants;
import tresc.benchmark.Constants.MappingLanguageType;
import tresc.benchmark.Modules;
import tresc.benchmark.Constants.ScenarioName;
import tresc.benchmark.Constants.TrampXMLOutputSwitch;
import vtools.dataModel.expression.Query;
import vtools.dataModel.expression.SPJQuery;
import vtools.dataModel.expression.SelectClauseList;
import vtools.dataModel.expression.Union;
import vtools.dataModel.schema.Schema;
import vtools.utils.structures.AssociativeArray;

/*
 * Each generator of a scenario case subclasses this class.
 */
public abstract class AbstractScenarioGenerator implements ScenarioGenerator {
	
	static Logger log = Logger.getLogger(AbstractScenarioGenerator.class);
	
	protected final String _attributes = "abcdefghijklmnopqrstuvwxyz"; // Can
																		// only
																		// hold
																		// less
																		// than
																		// 26
																		// attributes
																		// in
																		// one
																		// mapping

	protected HashMap<String, Character> attrMap =
			new HashMap<String, Character>();
	protected PartialMapping m = null;
	protected Random _generator;
	protected MappingScenario scen;
	protected Configuration configuration;

	protected int repetitions;
	protected int numOfElements;
	protected int numOfElementsDeviation;
	protected int nesting;
	protected int nestingDeviation;
	protected int numOfSetElements;
	protected int numOfSetElementsDeviation;
	protected int keyWidth;
	protected int keyWidthDeviation;
	protected int joinKind;	
	protected int numOfParams;
	protected int numOfParamsDeviation;
	protected int numNewAttr;
	protected int typeOfSkolem;
	protected int numDelAttr;
	protected int srcReusePerc;
	protected int trgReusePerc;
	protected MappingLanguageType mapLang;
	protected int curRep;
	protected boolean doSchemaElReuse = false;
	protected Schema source;
	protected Schema target;
	protected SPJQuery pquery;
	
	protected TrampModelFactory fac;
	protected TrampXMLModel model;

	/**
	 * Creates one instance of this basic scenario or returns if
	 * there have already been created "repetitions" number of instances.
	 * @param scenario
	 * @param configuration
	 * @throws Exception 
	 */
	public void generateNextScenario(MappingScenario scenario, 
			Configuration configuration) throws Exception {
		if (curRep++ < repetitions) {
			log.debug("CREATE " + curRep + "th scenario of type <" 
					+ getScenType() + ">");
			// already created enough basic scenarios to start reusing?
			doSchemaElReuse = scenario.getNumBasicScen() 
					>= configuration.getReuseThreshold();
			createOneInstanceOfScenario(scenario, configuration);
		}
	}
	
	/**
	 *  call to generate "repetitions" number of instances of this basic
	 *  scenario at once
	 *  
	 * @param scenario
	 * @param configuration
	 * @throws Exception 
	 */
	@Override
	public void generateScenario(MappingScenario scenario,
			Configuration configuration) throws Exception {
		init(configuration, scenario);
		log.debug("CREATE " + repetitions + " scenarios of type <" + getScenType() + ">");
		
		for (curRep = 0; curRep < repetitions; curRep++)
			createOneInstanceOfScenario(scenario, configuration);
	}

	private void createOneInstanceOfScenario(MappingScenario scenario,
			Configuration configuration) throws Exception {
		initPartialMapping();
		genSchemas();
		log.debug("\n\nGENERATED SCHEMAS: \nSOURCE:\n" + m.getSourceRels().toString() 
				+ "\n\nTARGET:\n" + m.getTargetRels().toString());
		if (configuration.getTrampXMLOutputOption(TrampXMLOutputSwitch.Correspondences)) {
			genCorrespondences();
			log.debug("\n\nGENERATED CORRS: \n" + m.getCorrs().toString());
		}
		genMappings();
		log.debug("\n\nGENERATED MAPS: \n" + m.getMaps().toString());
		if (configuration.getTrampXMLOutputOption(TrampXMLOutputSwitch.Transformations)) {
			genTransformations();
			log.debug("\n\nGENERATED TRANS: \n" + m.getTrans().toString());
		}
		scenario.get_basicScens().put(getScenType() + "_" + curRep, m);
		log.debug("Repetition <" + curRep +"> is " + m.toString());
	}

	protected abstract void genCorrespondences();
	
	protected abstract void genMappings() throws Exception;
	
	protected abstract void genTransformations() throws Exception;
	
	protected void genSchemas() throws Exception {
		// create new schema elements for the scenario instance
		if (!doSchemaElReuse) {
			genSourceRels();
			genTargetRels();
		}
		// roll dice to determine whether source or target are reused
		else {
			boolean reuseSrc = _generator.nextInt(100) <= srcReusePerc;
			boolean reuseTrg = _generator.nextInt(100) <= trgReusePerc;
			
			// do not both reuse source and target
			if (reuseSrc && reuseTrg) {
				if (_generator.nextBoolean())
					reuseSrc = false;
				else
					reuseTrg = false;
			}
			
			if (reuseSrc)
				chooseSourceRels();
			else
				genSourceRels();
			
			if (reuseTrg)
				chooseTargetRels();
			else
				genTargetRels();
		}
	}

	/**
	 * Pick the source relations for the scenario from the 
	 * source schema created so far.
	 * 
	 * @throws Exception
	 */
	protected void chooseSourceRels() throws Exception {
		RelationType rel = getRandomRel(true);
		m.addSourceRel(rel);
	}
	
	/**
	 * Pick the target relations for the scenario from the
	 * target schema created so far.
	 * @throws Exception
	 */
	protected void chooseTargetRels() throws Exception {
		RelationType rel = getRandomRel(false);
		m.addTargetRel(rel);		
	}
	
	protected RelationType getRandomRel (boolean source, int minAttrs) {
		return getRandomRel(source, minAttrs, false);//TODO use xpath over XBeans?		
	}
	
	protected RelationType getRandomRel (boolean source, int minAttrs, boolean key) {
		List<RelationType> cand = new ArrayList<RelationType> ();
		
		for(RelationType r: model.getSchema(source).getRelationArray()) {
			boolean ok = !key || r.isSetPrimaryKey();
			ok &= r.sizeOfAttrArray() >= minAttrs;
			if (ok)
				cand.add(r);
		}
		
		return pickRel(cand);//TODO use xpath over XBeans?
	}
	
	protected RelationType getRandomRelWithNumAttr (boolean source, int numAttr) {
		List<RelationType> cand = new ArrayList<RelationType> ();
		
		for(RelationType r: model.getSchema(source).getRelationArray()) {
			if (r.sizeOfAttrArray() == numAttr)
				cand.add(r);
		}
		
		return pickRel(cand);
	}
	
	private RelationType pickRel (List<RelationType> rels) {
		int numRels = rels.size();
		if (numRels == 0)
			return null;
		int pos = _generator.nextInt(numRels);
		return rels.get(pos);
	}
	
	protected RelationType getRandomRel (boolean source) {
		int numRels = model.getNumRels(source);
		if (numRels == 0)
			return null;
		int pos = _generator.nextInt(numRels);
		RelationType rel = model.getRel(pos, source);
		return rel;
	}
	
	protected abstract void genSourceRels() throws Exception;

	protected abstract void genTargetRels() throws Exception;

	public void init(Configuration configuration,
			MappingScenario mappingScenario) {
		this.scen = mappingScenario;
		model = scen.getDoc();
		fac = scen.getDocFac();
		m = null;
		_generator = configuration.getRandomGenerator();
		this.configuration = configuration;
		
		// get parameters from configuration
		repetitions =
				configuration.getScenarioRepetitions(getScenType().ordinal());
		numOfElements = configuration
						.getParam(Constants.ParameterName.NumOfSubElements);
		numOfElementsDeviation =
				configuration
						.getDeviation(Constants.ParameterName.NumOfSubElements);
		nesting = configuration.getParam(Constants.ParameterName.NestingDepth);
		nestingDeviation = configuration
						.getDeviation(Constants.ParameterName.NestingDepth);
		numOfSetElements =
				configuration.getParam(Constants.ParameterName.JoinSize);
		numOfSetElementsDeviation =
				configuration.getDeviation(Constants.ParameterName.JoinSize);
		keyWidth =
				configuration
						.getParam(Constants.ParameterName.NumOfJoinAttributes);
		keyWidthDeviation =
				configuration
						.getDeviation(Constants.ParameterName.NumOfJoinAttributes);
		joinKind = configuration.getParam(Constants.ParameterName.JoinKind);
		numOfParams =
				configuration
						.getParam(Constants.ParameterName.NumOfParamsInFunctions);
		numOfParamsDeviation =
				configuration
						.getDeviation(Constants.ParameterName.NumOfParamsInFunctions);
		numNewAttr =
				configuration
						.getParam(Constants.ParameterName.NumOfNewAttributes);
		typeOfSkolem =
				configuration.getParam(Constants.ParameterName.SkolemKind);
		numDelAttr =
				configuration
						.getParam(Constants.ParameterName.NumofAttributestoDelete);
		srcReusePerc =
				configuration.getParam(Constants.ParameterName.ReuseSourcePerc);
		trgReusePerc = configuration.getParam(Constants.ParameterName.ReuseTargetPerc);
        
        mapLang = configuration.getMapType();

		source = scen.getSource();
		target = scen.getTarget();
		pquery = scen.getTransformation();

		curRep = 0;
	}

	protected void initPartialMapping() {
		m = new PartialMapping();
		fac.setPartialMapping(m);
	}

	public abstract ScenarioName getScenType();

	public int getNestingDeviation() {
		return nestingDeviation;
	}

	public void setNestingDeviation(int nestingDeviation) {
		this.nestingDeviation = nestingDeviation;
	}

	protected int getRepetitions() {
		return repetitions;
	}

	protected void setRepetitions(int repetitions) {
		this.repetitions = repetitions;
	}

	protected int getNumOfElements() {
		return numOfElements;
	}

	protected void setNumOfElements(int numOfElements) {
		this.numOfElements = numOfElements;
	}

	protected int getNumOfElementsDeviation() {
		return numOfElementsDeviation;
	}

	protected void setNumOfElementsDeviation(int numOfElementsDeviation) {
		this.numOfElementsDeviation = numOfElementsDeviation;
	}

	protected int getNesting() {
		return nesting;
	}

	protected void setNesting(int nesting) {
		this.nesting = nesting;
	}

	public String getStamp() {
		return Constants.nameForScenarios.get(getScenType()).substring(1);
	}
	
	protected RelationType createFreeRandomRel (int relId, int numAttr) {
		RelationType r = RelationType.Factory.newInstance();
		r.setName(randomRelName(relId));
		for(int i = 0; i < numAttr; i++) {
			AttrDefType a = r.addNewAttr();
			a.setName(randomAttrName(relId, i));
			a.setDataType("TEXT");
		}
		
		return r;
	}
	
	protected String randomRelName(int relNum) {
		String randomName = Modules.nameFactory.getARandomName();
		String name =
				randomName + "_" + getStamp() + curRep + "NL"
						+ 0 + "CE" + relNum;
		return name.toLowerCase();
	}
	
	protected String randomAttrName(int relNum, int attrNum) {
		String randomName = Modules.nameFactory.getARandomName();
		String name = randomName + "_" + getStamp() + curRep + "NL"
						+ relNum + "AE" + attrNum;
		return name.toLowerCase();
	}
	
	protected String getAttrHook (int relNum, int attrNum) {
		return getStamp() + curRep + "NL" + relNum + "AE" + attrNum;
	}
	
	protected String getRelHook (int relNum) {
		return getStamp() + curRep + "NL" + 0 + "CE" + relNum;
	}
	
	protected void addCorr (int sRel, int sAttr, int tRel, int tAttr) {
		String toRel = m.getTargetRels().get(tRel).getName();
		String fromRel = m.getSourceRels().get(sRel).getName();
		String fromAttr = m.getAttrId(sRel, sAttr, true);
		String toAttr = m.getAttrId(tRel, tAttr, false);
		fac.addCorrespondence(fromRel, fromAttr, toRel, toAttr);
	}
	
	protected void addFK (int fRel, String[] fAttr, int tRel, String[] tAttr, boolean source) {
		String fromRel = m.getRelName(fRel, source);
		String toRel = m.getRelName(tRel, source);
		fac.addForeignKey(fromRel, fAttr, toRel, tAttr, source);
	}
	
	protected void addFK (int fRel, int fAttr, int tRel, int tAttr, boolean source) {
		String fromRel = m.getRelName(fRel, source);
		String fromAttr = m.getAttrId(fRel, fAttr, source);
		String toRel = m.getRelName(tRel, source);
		String toAttr = m.getAttrId(tRel, tAttr, source);
		fac.addForeignKey(fromRel, fromAttr, toRel, toAttr, source);
	}
	
	protected Query addQueryOrUnion (String tName, Query q) {
		SelectClauseList pselect = pquery.getSelect();
		String tblTrgName = tName;
		m.addQuery(q);
		
		// if exists merge into one query //TODO outsource this to provide different types of merging?
		if (pselect.getTerm(tName) != null) {
			Union u;
			Query other = (Query) pselect.getValue(tName);
			if (other instanceof Union) {
				u = (Union) other;
				u.add(q);
			}
			else {
				u = new Union();
				u.add(other);
				u.add(q);
				pselect.setValueOf(tName, u);
			}
			return u;
		}
		else {
			pselect.add(tblTrgName, q);
			pquery.setSelect(pselect);
			return q;
		}
	}
}
