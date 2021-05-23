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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class IniEntry
{
	public static final String VALUE_DELIMITER = "->";
	public static final String COMMENT_DELIMITER = "#";
	
	public static final String SECTION_START = "[";
	public static final String SECTION_END= "]";
	
	public String key;
	public String value;
	
	public static IniEntry readIniEntry(BufferedReader br) throws IOException
	{
		while(true)
		{
			String line = br.readLine();

			if(line == null)
			{
				return null;
			}

			int index = line.indexOf(COMMENT_DELIMITER);

			if(index >= 0)
			{
				line = line.substring(0, index);
			}

			line = line.trim();

			if(line.length() == 0)
			{
				continue;
			}
			
			index = line.indexOf(VALUE_DELIMITER);
			
			if(index >= 0)
			{
				IniEntry entry = new IniEntry();
				
				entry.key = line.substring(0, index).trim();
				entry.value = line.substring(index + VALUE_DELIMITER.length()).trim();
				
				return entry;
			}
			
			if(line.startsWith(SECTION_START) && line.endsWith(SECTION_END))
			{
				IniEntry entry = new IniEntry();
				
				entry.key = null;
				entry.value = line.substring(SECTION_START.length(), line.length() - SECTION_END.length()).trim();
				
				return entry;
			}
		}
	}
	
	public static ArrayList<IniEntry> readIniFile(String filename, boolean sections) throws IOException
	{
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));

		ArrayList<IniEntry> entries = new ArrayList();
		IniEntry entry;

		while((entry = readIniEntry(br)) != null)
		{
			if(sections || entry.key != null)
			{
				entries.add(entry);
			}
		}

		br.close();
		
		return entries;
	}
}