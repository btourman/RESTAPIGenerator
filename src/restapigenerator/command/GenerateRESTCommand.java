package restapigenerator.command;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.handlers.HandlerUtil;

public class GenerateRESTCommand extends AbstractHandler {

	private final String CR = "\n";
	private final String TAB = "\t";
	private final String DTAB = TAB + TAB;
	private final String DCR = CR + CR;
	private final String MODULE = "Module";
	private final String IMODULE = "IModule";

	private final String JAVAX_WS = "javax.ws.rs-api-2.0.jar";
	private final String URL_JAVAX_WS = "http://central.maven.org/maven2/javax/ws/rs/javax.ws.rs-api/2.0/" + JAVAX_WS;

	private final String JAXRS = "jaxrs-api-3.0.10.Final.jar";
	private final String URL_JAXRS = "http://central.maven.org/maven2/org/jboss/resteasy/jaxrs-api/3.0.10.Final/" + JAXRS;

	private final String REASTEASY_JAXRS = "resteasy-jaxrs-3.0.10.Final.jar";
	private final String URL_RESTEASY_JAXRS = "http://central.maven.org/maven2/org/jboss/resteasy/resteasy-jaxrs/3.0.10.Final/" + REASTEASY_JAXRS;

	private final String JACKSON_CORE = "jackson-core-2.5.1.jar";
	private final String URL_JACKSON_CORE = "http://central.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.5.1/" + JACKSON_CORE;

	private final String JACKSON_DATABIND = "jackson-databind-2.5.1.jar";
	private final String URL_JACKSON_DATABIND = "http://central.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.5.1/" + JACKSON_DATABIND;

	private final String JACKSON_ANNOTATIONS = "jackson-annotations-2.5.1.jar";
	private final String URL_JACKSON_ANNOTATIONS = "http://central.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.5.1/" + JACKSON_ANNOTATIONS;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		InputDialog packageDlg = new InputDialog(HandlerUtil.getActiveShellChecked(event), "Package Name", "Enter package name", "com.sample", null);

		InputDialog projectDlg = new InputDialog(HandlerUtil.getActiveShellChecked(event), "Project Name", "Enter project name", "Sample", null);

		if (projectDlg.open() == Window.OK && packageDlg.open() == Window.OK) {
			String projectName = projectDlg.getValue();
			String packageName = packageDlg.getValue();

			try {
				IProject project = generateProject(projectName);

				IJavaProject javaProject = JavaCore.create(project);

				addJavaLibrary(javaProject);

				IFolder sourceFolder = createFolder(project, "src");

				addClassPath(javaProject, JavaCore.newSourceEntry(javaProject.getPackageFragmentRoot(sourceFolder).getPath()));

				IFolder webContent = createFolder(project, "WebContent");
				IFolder webinfFolder = createFolder(project, "WebContent/WEB-INF");
				IFolder libFolder = createFolder(project, "WebContent/WEB-INF/lib");

				IPackageFragment pack = generatePackages(javaProject, sourceFolder, packageName);
				IPackageFragment impl = generatePackages(javaProject, sourceFolder, packageName + ".impl");
				IPackageFragment itf = generatePackages(javaProject, sourceFolder, packageName + ".itf");

				generateSources(pack, projectName, getClassExtendsApplication(pack.getElementName(), projectName));

				generateSources(impl, MODULE, getImplBody(impl.getElementName(), MODULE, itf.getElementName(), IMODULE));

				generateSources(itf, IMODULE, getItfBody(itf.getElementName(), IMODULE));

				downloadDependencies(libFolder.getLocation().toString(), javaProject);

				generateWebXml(webinfFolder.getLocation().toString(), pack.getElementName() + "." + projectName, webinfFolder);

			} catch (IOException e) {
				e.printStackTrace();
			} catch (JavaModelException e) {
				e.printStackTrace();
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	private IProject generateProject(String projectName) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(projectName);
		project.create(null);
		project.open(null);

		IProjectDescription description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);

		return project;
	}

	private IFolder createFolder(IProject project, String name) throws CoreException {
		IFolder folder = project.getFolder(name);
		folder.create(false, true, null);

		return folder;
	}

	private void addJavaLibrary(IJavaProject javaProject) throws JavaModelException {
		List<IClasspathEntry> entries = new ArrayList<IClasspathEntry>();
		IVMInstall vmInstall = JavaRuntime.getDefaultVMInstall();
		LibraryLocation[] locations = JavaRuntime.getLibraryLocations(vmInstall);
		for (LibraryLocation element : locations) {
			entries.add(JavaCore.newLibraryEntry(element.getSystemLibraryPath(), null, null));
		}
		setClassPath(javaProject, entries.toArray(new IClasspathEntry[entries.size()]));
	}

	private void addClassPath(IJavaProject javaProject, IClasspathEntry entry) throws JavaModelException {
		IClasspathEntry[] oldEntries = javaProject.getRawClasspath();
		IClasspathEntry[] newEntries = new IClasspathEntry[oldEntries.length + 1];
		System.arraycopy(oldEntries, 0, newEntries, 0, oldEntries.length);
		newEntries[oldEntries.length] = entry;
		setClassPath(javaProject, newEntries);
	}

	private void setClassPath(IJavaProject javaProject, IClasspathEntry[] entries) throws JavaModelException {
		javaProject.setRawClasspath(entries, null);
	}

	private IPackageFragment generatePackages(IJavaProject javaProject, IFolder sourceFolder, String packageName) throws JavaModelException {
		IPackageFragment pack = javaProject.getPackageFragmentRoot(sourceFolder).createPackageFragment(packageName, false, null);

		return pack;
	}

	private void generateSources(IPackageFragment pack, String className, String body) throws JavaModelException {

		className = className + ".java";

		pack.createCompilationUnit(className, body, false, null);
	}

	private void generateWebXml(String dir, String fullPathClassName, IFolder packageFolder) throws IOException, CoreException {
		File file = new File(dir, "web.xml");

		if (!file.exists()) {
			file.createNewFile();
		}

		FileWriter fw = new FileWriter(file.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(getWebXmlBody(fullPathClassName));
		bw.close();

		IFile ifile = packageFolder.getFile(file.getName());
		ifile.createLink(new Path(file.getAbsolutePath()), IResource.REPLACE, null);

	}

	private String getWebXmlBody(String fullPathClassName) {
		StringBuilder builder = new StringBuilder();

		builder.append("<!DOCTYPE web-app PUBLIC \"-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN\" \"http://java.sun.com/dtd/web-app_2_3.dtd\" >");
		builder.append(CR);

		builder.append("<web-app id=\"WebApp_ID\" version=\"2.4\" xmlns=\"http://java.sun.com/xml/ns/j2ee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd\">");
		builder.append(CR);

		builder.append("<display-name>Restful Web Application</display-name>");
		builder.append(DCR);

		builder.append("<context-param>");
		builder.append(CR);

		builder.append(TAB);
		builder.append("<param-name>resteasy.scan</param-name>");
		builder.append(CR);

		builder.append(TAB);
		builder.append("<param-value>true</param-value>");
		builder.append(CR);

		builder.append("</context-param>");
		builder.append(DCR);

		builder.append("<context-param>");
		builder.append(CR);

		builder.append(TAB);
		builder.append("<param-name>resteasy.resources</param-name>");
		builder.append(CR);

		builder.append(TAB);
		builder.append("<param-value>" + fullPathClassName + "</param-value>");
		builder.append(CR);

		builder.append("</context-param>");
		builder.append(DCR);

		builder.append("<listener>");
		builder.append(CR);

		builder.append(TAB);
		builder.append("<listener-class>");
		builder.append(CR);

		builder.append(DTAB);
		builder.append("org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap");
		builder.append(CR);

		builder.append(TAB);
		builder.append("</listener-class>");
		builder.append(CR);

		builder.append("</listener>");
		builder.append(DCR);

		builder.append("<servlet>");
		builder.append(CR);

		builder.append(TAB);
		builder.append("<servlet-name>Resteasy</servlet-name>");
		builder.append(CR);

		builder.append(DTAB);
		builder.append("<servlet-class>org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher</servlet-class>");
		builder.append(CR);

		builder.append(DTAB);
		builder.append("<init-param>");
		builder.append(CR);

		builder.append(DTAB);
		builder.append(TAB);
		builder.append("<param-name>javax.ws.rs.Application</param-name>");
		builder.append(CR);

		builder.append(DTAB);
		builder.append(TAB);
		builder.append("<param-value>" + fullPathClassName + "</param-value>");
		builder.append(CR);

		builder.append(DTAB);
		builder.append("</init-param>");
		builder.append(CR);

		builder.append(TAB);
		builder.append("</servlet>");
		builder.append(DCR);

		builder.append(TAB);
		builder.append("<servlet-mapping>");
		builder.append(CR);

		builder.append(DTAB);
		builder.append("<servlet-name>Resteasy</servlet-name>");
		builder.append(CR);

		builder.append(DTAB);
		builder.append("<url-pattern>/*</url-pattern>");
		builder.append(CR);

		builder.append(TAB);
		builder.append("</servlet-mapping>");
		builder.append(CR);

		builder.append("</web-app>");
		builder.append(CR);

		return builder.toString();

	}

	private String getItfBody(String packageName, String className) {
		StringBuilder builder = new StringBuilder();

		builder.append("package ");
		builder.append(packageName);
		builder.append(";");
		builder.append(DCR);

		builder.append("import javax.ws.rs.GET;");
		builder.append(CR);
		builder.append("import javax.ws.rs.Path;");
		builder.append(CR);
		builder.append("import javax.ws.rs.PathParam;");
		builder.append(CR);
		builder.append("import javax.ws.rs.Produces;");
		builder.append(CR);
		builder.append("import javax.ws.rs.core.MediaType;");
		builder.append(CR);
		builder.append("import javax.ws.rs.core.Response;");
		builder.append(DCR);

		builder.append("@Path(\"\")");
		builder.append(CR);
		builder.append("public interface IModule {");
		builder.append(DCR);

		builder.append(TAB);
		builder.append("@GET");
		builder.append(CR);
		builder.append(TAB);
		builder.append("@Path(\"/{id}\")");
		builder.append(CR);
		builder.append(TAB);
		builder.append("@Produces(MediaType.APPLICATION_JSON)");
		builder.append(CR);
		builder.append(TAB);
		builder.append("public Response sample(@PathParam(\"id\")int id);");
		builder.append(CR);
		builder.append("}");

		return builder.toString();
	}

	private String getImplBody(String packageName, String className, String packageItf, String ItfName) {
		StringBuilder builder = new StringBuilder();

		builder.append("package ");
		builder.append(packageName);
		builder.append(";");
		builder.append(DCR);

		builder.append("import javax.ws.rs.core.Response;");
		builder.append(CR);
		builder.append("import javax.ws.rs.core.Response.Status;");
		builder.append(CR);
		builder.append("import ");
		builder.append(packageItf);
		builder.append("." + ItfName);
		builder.append(";");
		builder.append(DCR);

		builder.append("public class ");
		builder.append(className);
		builder.append(" implements ");
		builder.append(ItfName);
		builder.append("{");
		builder.append(DCR);

		builder.append(TAB);
		builder.append("@Override");
		builder.append(CR);
		builder.append(TAB);
		builder.append("public Response sample(int id) {");
		builder.append(CR);
		builder.append(DTAB);
		builder.append("String mot = \"mot\";");
		builder.append(CR);
		builder.append(DTAB);
		builder.append("return Response.status(Status.OK).entity(mot).build();");
		builder.append(CR);
		builder.append(TAB);
		builder.append("}");
		builder.append(CR);
		builder.append("}");

		return builder.toString();
	}

	private String getClassExtendsApplication(String packageName, String className) {
		StringBuilder builder = new StringBuilder();

		builder.append("package ");
		builder.append(packageName);
		builder.append(";");
		builder.append(CR);
		builder.append("import java.util.HashSet;");
		builder.append(CR);
		builder.append("import java.util.Set;");
		builder.append(DCR);

		builder.append("import javax.ws.rs.Path;");
		builder.append(CR);
		builder.append("import javax.ws.rs.core.Application;");
		builder.append(DCR);

		builder.append("import ");
		builder.append(packageName);
		builder.append(".impl.Module;");
		builder.append(DCR);

		builder.append("@Path(\"/\")");
		builder.append(CR);
		builder.append("public class ");
		builder.append(className);
		builder.append(" extends Application{");
		builder.append(DCR);

		builder.append(TAB);
		builder.append("@Override");
		builder.append(CR);
		builder.append(TAB);
		builder.append("public Set<Class<?>> getClasses() {");
		builder.append(CR);
		builder.append(DTAB);
		builder.append("Set<Class<?>> classes = new HashSet<Class<?>>();");
		builder.append(CR);
		builder.append(DTAB);
		builder.append("classes.add(Module.class);");
		builder.append(CR);
		builder.append(DTAB);
		builder.append("return classes;");
		builder.append(CR);
		builder.append(TAB);
		builder.append("}");
		builder.append(CR);
		builder.append("}");

		return builder.toString();

	}

	private void downloadDependencies(String dir, IJavaProject javaProject) throws IOException, JavaModelException {
		downloadJar(URL_JAVAX_WS, dir, JAVAX_WS);
		downloadJar(URL_JAXRS, dir, JAXRS);
		downloadJar(URL_RESTEASY_JAXRS, dir, REASTEASY_JAXRS);
		downloadJar(URL_JACKSON_CORE, dir, JACKSON_CORE);
		downloadJar(URL_JACKSON_DATABIND, dir, JACKSON_DATABIND);
		downloadJar(URL_JACKSON_ANNOTATIONS, dir, JACKSON_ANNOTATIONS);

		IPath resteasy = new Path(dir + "/" + REASTEASY_JAXRS);
		IPath jaxrs = new Path(dir + "/" + JAXRS);
		IPath javaxws = new Path(dir + "/" + JAVAX_WS);
		IPath jacksonCore = new Path(dir + "/" + JACKSON_CORE);
		IPath jacksonDatabind = new Path(dir + "/" + JACKSON_DATABIND);
		IPath jacksonAnnotations = new Path(dir + "/" + JACKSON_ANNOTATIONS);

		addClassPath(javaProject, JavaCore.newLibraryEntry(resteasy, null, null));
		addClassPath(javaProject, JavaCore.newLibraryEntry(jaxrs, null, null));
		addClassPath(javaProject, JavaCore.newLibraryEntry(javaxws, null, null));
		addClassPath(javaProject, JavaCore.newLibraryEntry(jacksonCore, null, null));
		addClassPath(javaProject, JavaCore.newLibraryEntry(jacksonDatabind, null, null));
		addClassPath(javaProject, JavaCore.newLibraryEntry(jacksonAnnotations, null, null));

	}

	private void downloadJar(String strUrl, String dir, String name) throws MalformedURLException {
		URL url = new URL(strUrl);
		InputStream is = null;
		OutputStream os = null;
		try {
			os = new FileOutputStream(new File(dir + "/" + name));
			is = url.openStream();
			int inByte;
			while ((inByte = is.read()) != -1)
				os.write(inByte);
		} catch (IOException exp) {
			exp.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (Exception exp) {
			}
			try {
				os.close();
			} catch (Exception exp) {
			}
		}
	}
}
