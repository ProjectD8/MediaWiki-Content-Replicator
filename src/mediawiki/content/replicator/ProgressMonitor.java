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

public interface ProgressMonitor
{
	public void resetLog();
	
	public void print(String text);
	public void println(String text);
	public void println();
	public void showErrMsg(Throwable ex);
	
	public void initProgress(boolean operation, boolean page, boolean project);
	
	public boolean isOperationMonitored();
	public boolean isPageMonitored();
	public boolean isProjectMonitored();
	
	public int getOperationLimit();
	public int getOperationProgress();
	public void setOperationLimit(int limit);
	public void setOperationProgress(int value);
	public void progressOperation(int amount);
	
	public int getPageLimit();
	public int getPageProgress();
	public void setPageLimit(int limit);
	public void setPageProgress(int value);
	public void progressPage(int amount);
	
	public int getProjectLimit();
	public int getProjectProgress();
	public void setProjectLimit(int limit);
	public void setProjectProgress(int value);
	public void progressProject(int amount);
	
	public boolean isCancelled();
	public void setCancelled(boolean cancelled);
}
