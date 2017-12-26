/**
 * Copyright 2009-2017 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.parsing;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.List;

import org.apache.ibatis.io.Resources;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class XPathParserTest {

    @Test
    public void shouldTestXPathParserMethods() throws Exception {
        String resource = "resources/nodelet_test.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);
        XPathParser parser = new XPathParser(inputStream, false, null, null);
        assertEquals((Long) 1970l, parser.evalLong("/employee/birth_date/year"));
        assertEquals((short) 6, (short) parser.evalShort("/employee/birth_date/month"));
        assertEquals((Integer) 15, parser.evalInteger("/employee/birth_date/day"));
        assertEquals((Float) 5.8f, parser.evalFloat("/employee/height"));
        assertEquals((Double) 5.8d, parser.evalDouble("/employee/height"));
        assertEquals("${id_var}", parser.evalString("/employee/@id"));
        assertEquals(Boolean.TRUE, parser.evalBoolean("/employee/active"));
        assertEquals("<id>${id_var}</id>", parser.evalNode("/employee/@id").toString().trim());
        assertEquals(7, parser.evalNodes("/employee/*").size());
        XNode node = parser.evalNode("/employee/height");
        assertEquals("employee/height", node.getPath());
        assertEquals("employee[${id_var}]_height", node.getValueBasedIdentifier());
        inputStream.close();
    }


    @Test
    public void testXpath() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(false);
        factory.setCoalescing(false);
        factory.setExpandEntityReferences(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(Resources.getResourceAsStream("resources/nodelet_test.xml"));
        XPathFactory pathFactory = XPathFactory.newInstance();

        final XPath xpath = pathFactory.newXPath();
       System.out.println(
               xpath.evaluate("/employee/@id",document)+" "
               +xpath.evaluate("/employee/first_name",document)+" "
               +xpath.evaluate("/employee/last_name",document)
               +"的生日是："
               +xpath.evaluate("/employee/birth_date/year",document)+"年"
               +xpath.evaluate("/employee/birth_date/month",document)+"月"
               +xpath.evaluate("/employee/birth_date/day",document)+"日");
    }

}

