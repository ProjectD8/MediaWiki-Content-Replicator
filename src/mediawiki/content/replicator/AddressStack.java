/*
 * Copyright 2015 Kulikov Dmitriy
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
 */

package mediawiki.content.replicator;

import java.util.Stack;

public class AddressStack
{
	protected Stack<String> points = new Stack();
	protected char separator;
	
	protected String result = null;
	
	public AddressStack(char separator)
	{
		this.separator = separator;
	}
	
	public String enter(String element)
	{
		points.push(element);
		result = null;
		
		return element;
	}
	
	public String leave()
	{
		result = null;
		return points.pop();
	}
	
	public String toString()
	{
		if(result == null)
		{
			if(points.isEmpty())
			{
				result = "";
			}
			else
			{
				StringBuilder buf = new StringBuilder();
				buf.append(points.firstElement());

				for(int i = 1; i < points.size(); i++)
				{
					buf.append(separator).append(points.get(i));
				}

				result = buf.toString();
			}
		}
		
		return result;
	}
	
	public boolean equals(Object value)
	{
		return toString().equals(value);
	}
	
	public int hashCode()
	{
		return toString().hashCode();
	}
}