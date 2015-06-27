package com.tracepot.plugins.gradle

import com.android.build.gradle.api.ApplicationVariant
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.params.ConnRoutePNames
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.util.regex.Pattern

class TracepotPlugin implements Plugin<Project>
{
    @Override
    void apply(Project project)
    {
        project.configure(project) {
            if (!it.hasProperty("android")) {
                return
            }

            def extension = project.extensions.create("tracepotConfig", TracepotExtension)

            project.("android").applicationVariants.all { ApplicationVariant variant ->

                def variantName = variant.name

                // check if this variant is enabled
                if (!extension.enabledFor.contains(variantName)) {
                    return;
                }

                def packageName = variant.applicationId
                def newTaskName = "tracepot${variantName.capitalize()}"

                def newTask = project.task(newTaskName) << {
                    assertValidApiKey  extension

                    def mappingFilename = ""

                    if (variant.buildType.isMinifyEnabled()) {
                        project.logger.debug("Minify is enabled")

                        mappingFilename = variant.getMappingFile().toString()

                        project.logger.info("Using mapping ${mappingFilename}")
                    }

                    def manifestFile = variant.outputs.processManifest.manifestOutputFile
                    def manifest     = new XmlSlurper().parse(manifestFile.get(0))

                    String iconRes = manifest.application.'@android:icon'   //@drawable/ic_launcher || @mipmap/ic_launcher
                    String nameRes = manifest.application.'@android:label'  //@string/app_name  || app name
                    String resDir  = variant.outputs.processResources.resDir.get(0)

                    project.logger.info("Resource dir ${resDir}")

                    def iconFilename = findIcon(iconRes, resDir, project.logger)
                    project.logger.info("Using icon ${iconFilename}")

                    def appName = findName(nameRes, resDir, project.logger)
                    project.logger.info("Using name ${appName}")

                    upload(extension, mappingFilename, iconFilename, appName, packageName, variant.versionCode)
                    println "  Successfully uploaded to Tracepot"
                }

                newTask.dependsOn variant.dex
                variant.assemble.dependsOn newTaskName
            }
        }
    }

    /**
     * Make sure ApiKey is configured and not empty.
     *
     * @param extension
     */
    private static void assertValidApiKey(extension)
    {
        if (extension.getApiGroupKey() == null || extension.getApiGroupKey().equals("")) {
            throw new GradleException("Please configure your Tracepot apiGroupKey before building")
        }
    }

    /**
     * Looking for icon file
     *
     * @param icon
     * @param resDir
     * @return
     */
    private static String findIcon(String icon, String resDir, Logger logger)
    {
        if (!icon.startsWith("@drawable") && !icon.startsWith("@mipmap")) {
            return icon
        }

        def iconType = icon.split("/")[0].substring(1)
        def baseName = icon.split("/")[1]
        def bestQual = ""
        def currQual = 999
        def quality  = "mdpi hdpi xhdpi xxhdpi xxxhdpi".split()

        logger.info("Icon ${iconType} ${baseName}")

        File dir = new File(resDir)

        def osSeparator = Pattern.quote(File.separator)

        dir.listFiles().each { File file ->

            // get last part of the path
            def dirName = (file.absolutePath.split(osSeparator)).last()

            // we want only drawables
            if (!dirName.startsWith(iconType)) {
                return
            }

            // look for the resource in each drawable dir
            File f = new File(file, "${baseName}.png")
            if (!f.exists()) {
                return
            }

            // find the best version of icon
            quality.eachWithIndex { String q, int i ->
                if (file.absolutePath.contains(q)) {

                    logger.debug("Found icon ${f.absolutePath}")

                    if (currQual > i) {
                        bestQual = f.absolutePath.toString()
                        currQual = i
                    }
                }
            }
        }

        return bestQual
    }

    /**
     * Looking for application name
     *
     * @param name
     * @param resDir
     * @return
     */
    private static String findName(String name, String resDir, Logger logger)
    {
        if (!name.startsWith("@string")) {
            return name
        }

        def baseName = name.split("/")[1]

        logger.info("Name string ${baseName}")

        File f = new File(resDir, "values${File.separatorChar}values.xml")
        if (!f.exists()) {
            logger.warn("values.xml file not found")
            return ""
        }

        def values = new XmlSlurper().parse(f)

        return values.string.findAll{ it.@name.equals(baseName) }.text()
    }

    /**
     * Upload a mapping file using /api/upload-mapping REST service.
     *
     * @param extension
     * @param mappingFilename
     * @param appName
     * @param iconFilename
     * @param packageName
     * @param versionCode
     */
    private static void upload(TracepotExtension extension, String mappingFilename, String iconFilename,
                        String appName, String packageName, int versionCode)
    {
        String url = "${extension.getApiEndpoint()}/api/1/app"

        DefaultHttpClient httpClient = buildHttpClient()
        HttpPost                post = new HttpPost(url)
        MultipartEntity       entity = new MultipartEntity()

        entity.addPart('api_group key', new StringBody(extension.getApiGroupKey()))
        entity.addPart('package_name',  new StringBody(packageName))
        entity.addPart('version_code',  new StringBody(versionCode.toString()))
        entity.addPart('app_name',      new StringBody(appName))

        if (!mappingFilename.empty) {
            entity.addPart('mapping_file', new FileBody(new File(mappingFilename)))
        }

        if (!iconFilename.empty) {
            entity.addPart('icon_file', new FileBody(new File(iconFilename)))
        }

        post.addHeader("User-Agent", "Tracepot Gradle Plugin")
        post.addHeader("X-Requested-With", "XMLHttpRequest")
        post.setEntity(entity)

        HttpResponse response = httpClient.execute(post)

        String json = EntityUtils.toString(response.getEntity())
        int    code = response.getStatusLine().getStatusCode();

        println "  ${json}"

        if (code != HttpStatus.SC_OK) {
            throw new GradleException("API request failed with code " + code)
        }
    }

    /**
     * Create http client with proxy support
     *
     * @return DefaultHttpClient
     */
    private static DefaultHttpClient buildHttpClient()
    {
        DefaultHttpClient httpClient = new DefaultHttpClient()

        def proxyHost = System.getProperty("http.proxyHost")

        if (proxyHost != null) {
            def proxyPort = Integer.parseInt(System.getProperty("http.proxyPort"))
            def proxyUser = System.getProperty("http.proxyUser")

            HttpHost proxy = new HttpHost(proxyHost, proxyPort)

            if (proxyUser != null) {
                AuthScope   authScope   = new AuthScope(proxyUser, proxyPort)
                Credentials credentials = new UsernamePasswordCredentials(proxyUser, System.getProperty("http.proxyPassword"))

                httpClient.getCredentialsProvider().setCredentials(authScope, credentials)
            }

            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy)
        }

        return httpClient
    }

}
