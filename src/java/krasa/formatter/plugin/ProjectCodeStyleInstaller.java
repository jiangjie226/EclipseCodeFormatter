/*
 * External Code Formatter Copyright (c) 2007-2009 Esko Luontola, www.orfjackal.net Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */

package krasa.formatter.plugin;

import static krasa.formatter.plugin.ProxyUtils.createProxy;

import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.serviceContainer.PlatformComponentManagerImpl;

import krasa.formatter.settings.Settings;

/**
 * Switches a project's {@link CodeStyleManager} to a eclipse formatter and back.
 *
 * @author Esko Luontola
 * @author Vojtech Krasa
 * @since 2.12.2007
 */
public class ProjectCodeStyleInstaller {

	private static final String CODE_STYLE_MANAGER_KEY = CodeStyleManager.class.getName();
	private static final Logger LOG = Logger.getInstance(ProjectCodeStyleInstaller.class.getName());

	@NotNull
	private final Project project;

	public ProjectCodeStyleInstaller(@NotNull Project project) {
		this.project = project;
	}

	@NotNull
	public Project getProject() {
		return project;
	}

	public EclipseCodeStyleManager install(@NotNull Settings settings) {
		CodeStyleManager currentManager = CodeStyleManager.getInstance(project);

		EclipseCodeStyleManager overridingObject;
		if (compatibleWith_2016_3_API()) {
			overridingObject = new EclipseCodeStyleManager_IJ_2016_3plus(currentManager, settings);
		} else {
			overridingObject = new EclipseCodeStyleManager(currentManager, settings);
		}
		CodeStyleManager proxy = createProxy(currentManager, overridingObject);
		// CodeStyleManager proxy = new ManualCodeStyleManagerDelegator(currentManager, overridingObject);

		LOG.info("Overriding " + currentManager.getClass().getCanonicalName() + " with "
				+ overridingObject.getClass().getCanonicalName() + "' for project '" + project.getName()
				+ "' using CGLIB proxy");
		registerCodeStyleManager(project, proxy);
		return overridingObject;
	}

	private boolean compatibleWith_2016_3_API() {
		Class<?> aClass = null;
		try {
			aClass = Class.forName("com.intellij.psi.codeStyle.ChangedRangesInfo");
		} catch (ClassNotFoundException e) {
		}
		return aClass != null;
	}

	/**
	 * Dmitry Jemerov in unrelated discussion: "Trying to replace IDEA's core components with your custom
	 * implementations is something that we consider a very bad idea, and it's pretty much guaranteed to break in future
	 * versions of IntelliJ IDEA. I certainly hope that you won't stomp on any other plugins doing that, because no one
	 * else is doing it. It would be better to find another approach to solving your problem."
	 * <p>
	 * lol
	 */
	private static void registerCodeStyleManager(@NotNull Project project, @NotNull CodeStyleManager newManager) {
		if (isNewApi()) {
			PlatformComponentManagerImpl platformComponentManager = (PlatformComponentManagerImpl) project;
			IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginId.getId("EclipseCodeFormatter"));
			platformComponentManager.registerServiceInstance(CodeStyleManager.class, newManager, plugin);
		} else {
			MutablePicoContainer container = (MutablePicoContainer) project.getPicoContainer();
			container.unregisterComponent(CODE_STYLE_MANAGER_KEY);
			container.registerComponentInstance(CODE_STYLE_MANAGER_KEY, newManager);
		}
	}

	private static boolean isNewApi() {
		ApplicationInfo appInfo = ApplicationInfoImpl.getInstance();
		return appInfo.getBuild().getBaselineVersion() >= 193;
	}
}                                                                                   
