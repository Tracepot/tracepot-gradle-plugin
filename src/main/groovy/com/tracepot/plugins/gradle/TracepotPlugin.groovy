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
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class TracepotPlugin implements Plugin<Project>
{
    private String package_name

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

                package_name = variant.applicationId

                def newTaskName = "tracepot${variantName.capitalize()}"

                def newTask = project.task(newTaskName) << {
                    assertValidApiKey  extension
                    assertValidGroupId extension

                    if (variant.buildType.isMinifyEnabled()) {
                        String proguardMappingFilename = variant.getMappingFile().toString()

                        println "Using proguard mapping file at ${proguardMappingFilename}"
                        uploadMappingFile(extension, proguardMappingFilename)
                        println "Successfully uploaded mapping file"
                    }

                    def manifestFile = variant.outputs.processManifest.manifestOutputFile
                    def manifest     = new XmlSlurper().parse(manifestFile.get(0))

                    String icon = manifest.application.'@android:icon'   //@drawable/ic_launcher
                    String name = manifest.application.'@android:label'  //@string/app_name  || app name

                    // /Users/majlo/AndroidstudioProjects/TracepotSample/app/build/intermediates/res/free/debug
                    String resDir = variant.outputs.processResources.resDir.get(0)

                    println icon
                    println name
                    println resDir

                    println findIcon(icon, resDir)
                    println findName(name, resDir)
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
        if (extension.getApiKey() == null || extension.getApiKey().equals("")) {
            throw new GradleException("Please configure your Tracepot apiKey before building")
        }
    }

    /**
     * Make sure GroupId is configured and valid.
     *
     * @param extension
     */
    private static void assertValidGroupId(extension)
    {
        String groupId = extension.getGroupId()

        if (groupId == null || groupId.equals("")) {
            throw new GradleException("Please configure your Tracepot groupId for application")
        }

        if (!groupId.matches("([0-9a-f]{8})")) {
            throw new GradleException("Your Tracepot groupId does not have correct format")
        }
    }

    private static String findIcon(String icon, String resDir)
    {
        def baseName = icon.split("/")[1]

        println baseName

        File f = new File(resDir, "drawable-xxhdpi-v4/${baseName}.png")
        if (f.exists()) {
            return f.absolutePath.toString()
        }

        f = new File(resDir, "drawable-xhdpi-v4/${baseName}.png")
        if (f.exists()) {
            return f.absolutePath.toString()
        }

        f = new File(resDir, "drawable-hdpi-v4/${baseName}.png")
        if (f.exists()) {
            return f.absolutePath.toString()
        }

        f = new File(resDir, "drawable-mdpi-v4/${baseName}.png")
        if (f.exists()) {
            return f.absolutePath.toString()
        }

        f = new File(resDir, "drawable/${baseName}.png")
        if (f.exists()) {
            return f.absolutePath.toString()
        }

        return ""
    }

    private static String findName(String name, String resDir)
    {
        if (!name.startsWith("@string")) {
            return name
        }

        def baseName = name.split("/")[1]

        println baseName

        File f = new File(resDir, "values/values.xml")
        if (!f.exists()) {
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
     */
    private void uploadMappingFile(TracepotExtension extension, String mappingFilename)
    {
        String apiEndpoint = extension.getApiEndpoint()
        String url = "${apiEndpoint}/api/upload-mapping"

        MultipartEntity entity = buildEntity(extension)

        entity.addPart('mapping_file', new FileBody(new File(mappingFilename)))

        post(url, entity)
    }

    /**
     * Build MultipartEntity with common values
     *
     * @param extension
     * @return MultipartEntity
     */
    private MultipartEntity buildEntity(TracepotExtension extension)
    {
        MultipartEntity entity = new MultipartEntity()

        entity.addPart('api_key',      new StringBody(extension.getApiKey()))
        entity.addPart('group_id',     new StringBody(extension.getGroupId()))
        entity.addPart('package_name', new StringBody(package_name))

        return entity
    }

    /**
     * Create http client with proxy support
     *
     * @return DefaultHttpClient
     */
    private static DefaultHttpClient buildHttpClient()
    {
        DefaultHttpClient httpClient = new DefaultHttpClient()

        // configure proxy (patched by timothy-volvo, https://github.com/timothy-volvo/testfairy-gradle-plugin)
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

    /**
     * Post data to API endpoint
     *
     * @param url
     * @param entity
     */
    private static void post(String url, MultipartEntity entity)
    {
        DefaultHttpClient httpClient = buildHttpClient()
        HttpPost                post = new HttpPost(url)

        post.addHeader("User-Agent", "Tracepot Gradle Plugin")
        post.setEntity(entity)

        HttpResponse response = httpClient.execute(post)

        int code = response.getStatusLine().getStatusCode();

        if (code != HttpStatus.SC_MOVED_TEMPORARILY) {
            throw new GradleException("API request failed with code " + code)
        }
    }


}
