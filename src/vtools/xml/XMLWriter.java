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
package vtools.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import smark.support.MappingScenario;
import smark.support.SMarkElement;
import tresc.benchmark.Configuration;
import tresc.benchmark.Constants.TrampXMLOutputSwitch;
import vtools.dataModel.expression.BooleanExpression;
import vtools.dataModel.expression.FromClauseList;
import vtools.dataModel.expression.Rule;
import vtools.dataModel.schema.Element;
import vtools.dataModel.schema.Schema;
import vtools.dataModel.types.Int;
import vtools.dataModel.types.Rcd;
import vtools.dataModel.types.Set;
import vtools.dataModel.types.Str;
import vtools.dataModel.types.Type;

public class XMLWriter {
	private final String _tab = "   ";

	private static final Comparator<String> ID_COMP = new Comparator<String>() {

		@Override
		public int compare(String l, String r) {
			char[] lChars = l.toCharArray();
			char[] rChars = r.toCharArray();
			int pos = 0;

			if (l.length() != r.length())
				return l.length() < r.length() ? -1 : 1;

			// skip same char
			while (lChars[pos] == rChars[pos++] && pos < l.length())
				;
			pos--;

			// both have number
			if (Character.isDigit(lChars[pos])
					&& Character.isDigit(rChars[pos])) {
				// assume the rest is a number
				int lId = Integer.parseInt(l.substring(pos));
				int rId = Integer.parseInt(r.substring(pos));
				if (lId == rId)
					return 0;
				return lId < rId ? -1 : 1;
			}
			// no order one that is digit first
			else
				return Character.isDigit(lChars[pos]) ? -1 : 1;
		}

	};

	public void print(StringBuffer buf, Object o, int ident) {
		throw new RuntimeException("DO not know how to print object "
				+ o.getClass().getName());
	}

	public void print(StringBuffer buf, Set set, int ident) {
		for (int i = 0, imax = set.size(); i < imax; i++) {
			Element ntpair = (Element) set.getField(i);
			print(buf, ntpair, ident);
		}
	}

	public void print(StringBuffer buf, Schema s, int ident) {
		// for (int i = 0; i < ident; i++)
		// buf.append(_tab);
		Rcd rcd = (Rcd) s.getType();
		for (int i = 0, imax = rcd.size(); i < imax; i++) {
			Element ntpair = (Element) rcd.getField(i);
			printRel(buf, ntpair, s, ident + 1);
		}
	}

	public void printRel(StringBuffer buf, Element e, Schema s, int ident) {
		Type type = e.getType();
		for (int i = 0; i < ident; i++)
			buf.append(_tab);
		buf.append("<Relation name=\"" + e.getLabel().toLowerCase() + "\">\n");
		print(buf, (Set) type, ident + 1);
		Rule primaryKey = s.getMyKeyConstraint(e.getLabel());
		if (primaryKey != null)
			printPrimaryKey(buf, e.getLabel(), primaryKey, ident + 1);
		for (int i = 0; i < ident; i++)
			buf.append(_tab);
		buf.append("</Relation>\n");
	}

	public void print(StringBuffer buf, Element e, int ident) {
		Type type = e.getType();
		for (int i = 0; i < ident; i++)
			buf.append(_tab);
		// buf.append("<xs:element name=\"" + e.getLabel() +
		// "\" minOccurs=\"0\"");

		if (type instanceof Set) {
			buf.append("<Relation name=\"" + e.getLabel().toLowerCase() + "\">\n");
			print(buf, (Set) type, ident + 1);
			for (int i = 0; i < ident; i++)
				buf.append(_tab);
			buf.append("</Relation>\n");
		}
		else if (type instanceof Rcd) {
			buf.append("<xs:element name=\"" + e.getLabel()
					+ "\" minOccurs=\"0\"");
			buf.append(" maxOccurs=\"1\">\n");
			print(buf, (Rcd) type, ident + 1);
			for (int i = 0; i < ident; i++)
				buf.append(_tab);
			buf.append("</xs:element>\n");
		}
		else if (type instanceof Int) {
			buf.append("<Attr><Name>" + e.getLabel().toLowerCase()
					+ "</Name><DataType>INT8</DataType></Attr>\n");
		}
		else if (type instanceof Str) {
			buf.append("<Attr><Name>" + e.getLabel().toLowerCase()
					+ "</Name><DataType>TEXT</DataType></Attr>\n");
		}
		else
			throw new RuntimeException("Do not know how to handle type "
					+ type.getClass().getName());
	}

	public void print(StringBuffer buf, MappingScenario scenario, int ident,
			String instancePathPrefix, Configuration conf) {
		Schema source = scenario.getSource();
		Schema target = scenario.getTarget();
		Map<String, String> correspondences = scenario.getCorrespondences();
		Map<String, List<String>> mappings2Correspondences =
				scenario.getMappings2Correspondences();
		Map<String, HashMap<String, List<Character>>> mappings2Sources =
				scenario.getMappings2Sources();
		Map<String, HashMap<String, List<Character>>> mappings2Targets =
				scenario.getMappings2Targets();
		Map<String, List<String>> transformationMappings =
				scenario.getTransformation2Mappings();
		Map<String, String> transformationCode =
				scenario.getTransformationCode();
		Map<String, String> transformationRelName =
				scenario.getTransformationRelName();

		// schemas should be of type record
		buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		buf.append("<this:MappingScenario xmlns:this=\"org/vagabond/xmlmodel\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");
		buf.append("<Schemas>\n");
		print(buf, source, ident, 0);
		print(buf, target, ident, 1);
		buf.append("</Schemas>\n");
		if (conf.getTrampXMLOutputOption(TrampXMLOutputSwitch.Correspondences)) {
			buf.append("<Correspondences>\n");
			printCorrespondences(buf, correspondences);
			buf.append("</Correspondences>\n");
		}
		buf.append("<Mappings>\n");
		printMappings(buf, mappings2Correspondences, mappings2Sources,
				mappings2Targets, conf);
		buf.append("</Mappings>\n");
		
		if (conf.getTrampXMLOutputOption(TrampXMLOutputSwitch.Transformations)) {
			buf.append("<Transformations>\n");
			printTransformations(buf, transformationMappings, transformationCode,
					transformationRelName);
			buf.append("</Transformations>\n");
		}
		
		if (conf.getTrampXMLOutputOption(TrampXMLOutputSwitch.ConnectionInfo))
			printConnectionInfo(buf);
		if (conf.getTrampXMLOutputOption(TrampXMLOutputSwitch.Data))
			printData(buf, scenario.getSource(), instancePathPrefix);
		buf.append("</this:MappingScenario>");
	}

	public void printTransformations(StringBuffer buf,
			Map<String, List<String>> tM, Map<String, String> tC,
			Map<String, String> tR) {
		List<String> sortedKeys = new ArrayList<String>(tC.keySet());
		Collections.sort(sortedKeys, ID_COMP);

		for (String tId : sortedKeys) {
			List<String> mappingList = tM.get(tId);
			buf.append(_tab + "<Transformation id=\"" + tId + "\" creates=\""
					+ tR.get(tId).toLowerCase() + "\">\n");
			if (mappingList != null) {
				buf.append(_tab + _tab + "<Implements>");
				for (String mapping : mappingList) {
					buf.append("<Mapping ref=\"" + mapping + "\" />");
				}
				buf.append("</Implements>\n");
			}
			// escape entities for XML
			String tCode = tC.get(tId);
			tCode = escapeXMLChars(tCode);
			buf.append(_tab + _tab + "<Code>\n");
			buf.append(tCode + "\n");
			buf.append(_tab + _tab + "</Code>\n");
			buf.append(_tab + "</Transformation>\n");
		}

		//
		// if (!tM.isEmpty()) {
		// for (Map.Entry<String, List<String>> entry : tM.entrySet()) {
		// String tId = entry.getKey();
		// List<String> mappingList = entry.getValue();
		// buf.append(_tab+"<Transformation id=\""+tId+"\" creates=\""+tR.get(tId)+"\">\n");
		// if (mappingList != null) {
		// buf.append(_tab+_tab+"<Implements>");
		// for (String mapping: mappingList) {
		// buf.append("<Mapping>"+mapping+"</Mapping>");
		// }
		// buf.append("</Implements>\n");
		// }
		// String tCode = tC.get(tId);
		// buf.append(_tab+_tab+"<Code>\n");
		// buf.append(tCode+"\n");
		// buf.append(_tab+_tab+"</Code>\n");
		// buf.append(_tab+"</Transformation>\n");
		// }
		// } else {//TODO isn't this unnessesary involved?
		// for (Map.Entry<String, String> entry : tR.entrySet()) {
		// String tId = entry.getKey();
		// buf.append(_tab+"<Transformation id=\""+tId+"\" creates=\""+tR.get(tId)+"\">\n");
		// buf.append(_tab+_tab+"<Implements>");
		// buf.append("<Mapping></Mapping>");
		// buf.append("</Implements>\n");
		// String tCode = tC.get(tId);
		// buf.append(_tab+_tab+"<Code>\n");
		// buf.append(tCode+"\n");
		// buf.append(_tab+_tab+"</Code>\n");
		// buf.append(_tab+"</Transformation>\n");
		// }
		// }

	}

	private String escapeXMLChars(String tCode) {
		StringBuffer result = new StringBuffer();
		char[] chars = tCode.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			switch (chars[i]) {
			case '<':
				result.append("&lt;");
				break;
			case '>':
				result.append("&gt;");
				break;
			case '"':
				result.append("&quot;");
				break;
			case '&':
				result.append("&amp;");
				break;
			case '\'':
				result.append("&apos;");
				break;
			default:
				result.append(chars[i]);
			}
		}

		return result.toString();
	}

	public void printMappings(StringBuffer buf,
			Map<String, List<String>> mC,
			Map<String, HashMap<String, List<Character>>> mS,
			Map<String, HashMap<String, List<Character>>> mT, 
			Configuration conf) {
		List<String> sortedKeys = new ArrayList<String>(mC.keySet());
		Collections.sort(sortedKeys, ID_COMP);

		for (String mId : sortedKeys) {
			buf.append(_tab + "<Mapping id=\"" + mId + "\">\n");
			
			if (conf.getTrampXMLOutputOption(TrampXMLOutputSwitch.Correspondences)) {
				List<String> corrList = mC.get(mId);
				if (corrList != null) {
					buf.append(_tab + _tab + "<Uses>\n");
					for (String corr : corrList) {
						buf.append(_tab + _tab + _tab + "<Correspondence ref=\""
								+ corr + "\" />\n");
					}
					buf.append(_tab + _tab + "</Uses>\n");
				}
			}

			Map<String, List<Character>> sourceList = mS.get(mId);
			if (sourceList != null) {
				buf.append(_tab + _tab + "<Foreach>\n");			
				for (Map.Entry<String, List<Character>> sourceEntry : sourceList
						.entrySet()) {
					String sourceName = sourceEntry.getKey();
					buf.append(_tab + _tab + _tab + "<Atom tableref=\""
							+ sourceName.toLowerCase() + "\">");
					List<Character> attrList = sourceEntry.getValue();
					for (Character attr : attrList) {
						buf.append("<Var>" + attr + "</Var>");
					}
					buf.append("</Atom>\n");
				}
				buf.append(_tab + _tab + "</Foreach>\n");
			}
			buf.append(_tab + _tab + "<Exists>\n");
			Map<String, List<Character>> targetList = mT.get(mId);
			for (Map.Entry<String, List<Character>> targetEntry : targetList
					.entrySet()) {
				String targetName = targetEntry.getKey();
				buf.append(_tab + _tab + _tab + "<Atom tableref=\""
						+ targetName.toLowerCase() + "\">");
				List<Character> attrList = targetEntry.getValue();
				for (Character attr : attrList) {
					buf.append("<Var>" + attr + "</Var>");
				}
				buf.append("</Atom>\n");
			}
			buf.append(_tab + _tab + "</Exists>\n");
			buf.append(_tab + "</Mapping>\n");
		}
	}

	private void printCorrespondences (StringBuffer buf, Map<String, String> correspondences) {
		List<String> sortedKeys =
				new ArrayList<String>(correspondences.keySet());
		Collections.sort(sortedKeys, ID_COMP);

		for (String key : sortedKeys) {
			String cId = key;
			String correspondence = correspondences.get(key);
			String[] corrArr = correspondence.split("=");
			String from = corrArr[0];
			String[] fromRelAttr = from.split("\\.");
			String to = corrArr[1];
			String[] toRelAttr = to.split("\\.");
			buf.append(_tab + "<Correspondence id=\"" + cId + "\">\n");
			buf.append(_tab + _tab + "<From tableref=\"");
			buf.append(fromRelAttr[0].toLowerCase());
			buf.append("\"><Attr>");
			buf.append(fromRelAttr[1].toLowerCase());
			buf.append("</Attr></From>\n");
			buf.append(_tab + _tab + "<To tableref=\"");
			buf.append(toRelAttr[0].toLowerCase());
			buf.append("\"><Attr>");
			buf.append(toRelAttr[1].toLowerCase());
			buf.append("</Attr></To>\n");
			buf.append(_tab + "</Correspondence>\n");
		}
	}

	private void printConnectionInfo(StringBuffer buf) {
		buf.append("<ConnectionInfo>\n");
		buf.append(_tab);
		buf.append("<Host>localhost</Host>\n");
		buf.append(_tab);
		buf.append("<DB>tramptest</DB>\n");
		buf.append(_tab);
		buf.append("<User>postgres</User>\n");
		buf.append(_tab);
		buf.append("<Password/>\n");
		buf.append("<Port>5432</Port>\n");
		buf.append("</ConnectionInfo>\n");
	}

	private void printData(StringBuffer buf, Schema schema,
			String instancePathPrefix) {
		if (schema.size() == 0)
			return;
		
		buf.append("<Data>\n");
		for (int i = 0; i < schema.size(); i++) {
			SMarkElement rootSetElt = (SMarkElement) schema.getSubElement(i);
			printDataSource(buf, rootSetElt, instancePathPrefix);
		}
		buf.append("</Data>\n");
	}

	private void printDataSource(StringBuffer buf, SMarkElement rel, 
			String path) {
		buf.append("\t<InstanceFile name=\"" + rel.getLabel().toLowerCase() + "\">\n" + "\t\t<Path>" + path
				
				+ "</Path>\n" + "\t\t<FileName>" + rel.getLabel()
				+ ".csv</FileName>\n" + "\t\t<ColumnDelim>|</ColumnDelim>\n"
				+ "\t</InstanceFile>\n");
	}

	private void printIdents(StringBuffer buf, int ident) {
		for (int i = 0; i < ident; i++)
			buf.append(_tab);

	}

	private void printForeignKeys(StringBuffer buf,
			List<Rule> constraints, int ident) {
		int i = 0;
		for (Rule constraint : constraints) {
			// the foreign keys are both ways. we need only one of them.
			// TODO however the direction is not irrelevant, we need the right
			// one
			if (i % 2 == 0) {
				printIdents(buf, ident);
				String id = getTableName(constraint.getLeftTerms()) + "_"
								+ getTableName(constraint.getRightTerms())
								+ Integer.toString(i);
				buf.append("<ForeignKey id=\"" + id + "\">\n");
				printIdents(buf, ident);
				buf.append(_tab + "<From tableref=\""
						+ getTableName(constraint.getLeftTerms()) + "\">");
				buf.append("<Attr>"
						+ getAttributes(constraint.getRightConditions())[1]
						+ "</Attr>");
				buf.append("</From>\n");
				printIdents(buf, ident);
				buf.append(_tab + "<To tableref=\""
						+ getTableName(constraint.getRightTerms()) + "\">");
				buf.append("<Attr>"
						+ getAttributes(constraint.getRightConditions())[0]
						+ "</Attr>");
				buf.append("</To>\n");
				printIdents(buf, ident);
				buf.append("</ForeignKey>\n");
			}
			i++;
		}
	}

	private void printPrimaryKey(StringBuffer buf, String relName,
			Rule constraint, int ident) {
		printIdents(buf, ident);
//		String id = relName + "_" + "PrimaryKey";
		buf.append("<PrimaryKey>\n"); // id=\"" + id + "\">\n");
		printIdents(buf, ident);
		buf.append(_tab + "<Attr>"
				+ getAttributes(constraint.getLeftConditions())[0]
				+ "</Attr>\n");
		printIdents(buf, ident);
		buf.append("</PrimaryKey>\n");

	}

	private String getTableName(FromClauseList cl) {
		int strLength = cl.toString().length();
		return cl.toString().substring(1, strLength - 3).toLowerCase();
	}

	private String[] getAttributes(BooleanExpression be) {
		String[] terms = be.toString().split("=");
		String fromAttribute = terms[0].substring(3);
		String toAttribute = terms[1].substring(3);
		String[] retVal = new String[2];
		retVal[0] = fromAttribute.toLowerCase();
		retVal[1] = toAttribute.toLowerCase();
		return retVal;
	}

	private void print(StringBuffer buf, Schema s, int ident, int schemaType) {
		if (schemaType == 0)
			buf.append(_tab + "<SourceSchema>\n");
		else
			buf.append(_tab + "<TargetSchema>\n");
		print(buf, s, 1);
		List<Rule> foreignKeys = s.getForeignKeyConstraints();
		printForeignKeys(buf, foreignKeys, ident + 2);
		if (schemaType == 0)
			buf.append(_tab + "</SourceSchema>\n");
		else
			buf.append(_tab + "</TargetSchema>\n");
	}
}
