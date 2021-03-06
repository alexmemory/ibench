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
package vtools.dataModel.schema;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import vtools.dataModel.expression.ForeignKey;
import vtools.dataModel.expression.Key;
import vtools.dataModel.expression.Rule;
import vtools.dataModel.types.Set;
import vtools.dataModel.types.Type;

public class HTMLPresenter extends vtools.dataModel.types.HTMLPresenter
{
    public static vtools.dataModel.schema.HTMLPresenter HTMLPresenter = new vtools.dataModel.schema.HTMLPresenter();


    /**
     * Prints the buffer (which should be in HTML) in a file which will serve
     * for the browser by putting before and after the HTML headers
     */
    public void printInHtmlFile(StringBuffer buf, String pathPrefix, String file)
    {

        StringBuffer fbuf = new StringBuffer();
        printHTMLHeader(fbuf);
        fbuf.append(buf);
        printHTMLFooter(fbuf);
        try
        {
            BufferedWriter bufWriter = new BufferedWriter(new FileWriter(new File(pathPrefix, file)));
            bufWriter.write(fbuf.toString());
            bufWriter.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }



    public Object visit(Schema o, Object[] args)
    {
        StringBuffer buf = (StringBuffer) args[0];
        Element e = (Element) o;
        visit(e, args);
        buf.append("<p><font color=\"red\"><b>Constraints:</b></font></p>\n");
        buf.append("<pre>\n");
        for (int i = 0, imax = o.getConstrSize(); i < imax; i++)
        {
            Rule constraint = o.getConstraint(i);
            if (constraint instanceof Key)
            {
                buf.append("// Key: ");
                String[][] l = ((Key) constraint).getHumanReadableRepresentation();
                for (int j = 0, jmax = l.length; j < jmax; j++)
                {
                    String[] m = l[j];
                    for (int k = 0, kmax = m.length; k < kmax; k++)
                    {
                        if (j != 0)
                        {
                            if (k != 0)
                                buf.append(".");
                            else buf.append(", ");
                        }
                        else if (k != 0)
                            buf.append(".");
                        buf.append(m[k]);
                    }
                    if (j == 0)
                        buf.append("[");
                }
                buf.append("]\n");
            }
            else if (constraint instanceof ForeignKey)
            {
                buf.append("// FKey: \n");
                String[][][] l = ((ForeignKey) constraint).getHumanReadableRepresentation();
                for (int j = 0, jmax = l.length; j < jmax; j++)
                {
                    String[] keyAttr = l[j][0];
                    String[] fkAttr = l[j][1];
                    buf.append("//           ");
                    for (int k = 0, kmax = fkAttr.length; k < kmax; k++)
                    {
                        if (k != 0)
                            buf.append(".");
                        buf.append(fkAttr[k]);
                    }
                    buf.append(" --> ");
                    for (int k = 0, kmax = keyAttr.length; k < kmax; k++)
                    {
                        if (k != 0)
                            buf.append(".");
                        buf.append(keyAttr[k]);
                    }
                    buf.append("\n");
                }
            }
            buf.append(constraint.toString());
            buf.append(";\n");
        }
        buf.append("</pre>\n");
        return null;
    }

    public Object visit(Element o, Object[] args)
    {
        StringBuffer buf = (StringBuffer) args[0];
        Integer tabsInteger = (Integer) args[1];
        int tabs = tabsInteger.intValue();
        for (int ij = 0; ij < tabs; ij++)
            buf.append("<img border=\"0\" SRC=\"images/transparent.gif\" height=\"0\" width=\"10\"/>");
        Type t = o.getType();
        if (t instanceof Set)
            buf.append("<font color=\"#005FA9\">");
        String label = o.getLabel();
        int lastInd = label.lastIndexOf("_");
        if (lastInd != -1) 
            label = label.substring(0, lastInd);
        buf.append("<b title=\"" + o.getHook() + "\">" + label + "&nbsp;&nbsp;</b>");
        if (t instanceof Set)
            buf.append("</font>");
        args[1] = new Integer(tabs + 1);
        t.accept(this, args);
        args[1] = tabsInteger;
        return null;
    }

    private void printHTMLHeader(StringBuffer buf)
    {
        buf.append("<html><head><meta http-equiv=\"Content-Language\" content=\"en-us\"><meta http-equiv=\"Content-Type\" content=\"text/html\">");
        buf.append("<style>\n<!--\nbody, p, tbody\n{\nfont-weight: normal;\nfont-family: Verdana, Arial, Helvetica, sans-serif;\nfont-size: 10pt;\n}\n-->\n</style>\n</head><body>");

    }

    private void printHTMLFooter(StringBuffer buf)
    {
        buf.append("</body></html>");
    }
}
