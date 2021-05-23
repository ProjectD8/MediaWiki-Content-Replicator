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

public class ProgressData
{
	protected int operationLimit;
	protected int operationProgress;
	
	protected int pageLimit;
	protected int pageProgress;
	
	protected int projectLimit;
	protected int projectProgress;
	
	protected boolean operationMonitored;
	protected boolean pageMonitored;
	protected boolean projectMonitored;
	
	public ProgressData()
	{
	}
	
	public ProgressData(ProgressMonitor progress)
	{
		save(progress);
	}
	
	public void save(ProgressMonitor progress)
	{
		operationLimit = progress.getOperationLimit();
		operationProgress = progress.getOperationProgress();
		operationMonitored = progress.isOperationMonitored();
		
		pageLimit = progress.getPageLimit();
		pageProgress = progress.getPageProgress();
		pageMonitored = progress.isPageMonitored();
		
		projectLimit = progress.getProjectLimit();
		projectProgress = progress.getProjectProgress();
		projectMonitored = progress.isProjectMonitored();
	}
	
	public void restore(ProgressMonitor progress)
	{
		progress.initProgress(operationMonitored, pageMonitored, projectMonitored);
		
		progress.setOperationLimit(operationLimit);
		progress.setOperationProgress(operationProgress);
		
		progress.setPageLimit(pageLimit);
		progress.setPageProgress(pageProgress);
		
		progress.setProjectLimit(projectLimit);
		progress.setProjectProgress(projectProgress);
	}
}
