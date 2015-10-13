// Copyright 2015 Sebastian Kuerten
//
// This file is part of dynsax.
//
// dynsax is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// dynsax is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with dynsax. If not, see <http://www.gnu.org/licenses/>.

package de.topobyte.xml.dynsax;

import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public abstract class DynamicSaxHandler extends DefaultHandler
{

	private Element root;
	private boolean emitRoot;

	private Stack<TreePosition> state = new Stack<TreePosition>();

	private class TreePosition
	{

		private Element element;
		private Child child;
		private Data data;

		public TreePosition(Element element, Child child, Data data)
		{
			this.element = element;
			this.child = child;
			this.data = data;
		}

	}

	public void setRoot(Element root, boolean emitRoot)
	{
		this.root = root;
		this.emitRoot = emitRoot;
		init(root);
	}

	private void init(Element element)
	{
		element.init();
		for (Child child : element.children) {
			init(child.element);
		}
	}

	public abstract void emit(Data data) throws ParsingException;

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException
	{
		if (state.isEmpty()) {
			if (qName.equals(root.identifier)) {
				Data data = new Data(root);
				getAttributes(data, attributes, root);
				state.push(new TreePosition(root, null, data));
			}
		} else {
			TreePosition top = state.peek();
			Element topElement = top.element;
			Child child = topElement.lookup.get(qName);
			if (child == null) {
				return;
			}
			Element childElement = child.element;
			Data data = new Data(childElement);
			getAttributes(data, attributes, childElement);
			state.push(new TreePosition(childElement, child, data));
		}
	}

	private void getAttributes(Data data, Attributes attributes, Element element)
	{
		for (String name : element.attributes) {
			// System.out.println("getting attribute: " + name);
			String value = attributes.getValue(name);
			if (value == null) {
				continue;
			}
			data.addAttribute(name, value);
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException
	{
		TreePosition top = state.peek();
		Element topElement = top.element;
		Child child = top.child;
		if (topElement.identifier.equals(qName)) {
			// System.out.println("finish: " + qName);
			state.pop();
			if (child == null) {
				// happens only with the root element
				if (emitRoot) {
					try {
						emit(top.data);
					} catch (ParsingException e) {
						throw new SAXException("while emitting root element", e);
					}
				}
				return;
			}
			Data data = top.data;
			if (child.emit) {
				try {
					emit(data);
				} catch (ParsingException e) {
					throw new SAXException("while emitting element", e);
				}
			}
			TreePosition parent = state.peek();
			Data parentData = parent.data;
			// System.out.println("parent: " + parent.element.identifier);
			if (child.type == ChildType.SINGLE) {
				// System.out.println("set single: " + data);
				parentData.setSingle(qName, data);
			} else if (child.type == ChildType.LIST) {
				// System.out.println("set list: " + data);
				parentData.addToList(qName, data);
			}
		}
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException
	{
		TreePosition top = state.peek();
		Data data = top.data;
		data.buffer.append(ch, start, length);
	}

}
