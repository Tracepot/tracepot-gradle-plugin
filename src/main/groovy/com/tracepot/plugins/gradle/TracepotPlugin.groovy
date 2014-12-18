package com.tracepot.plugins.gradle

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
    @Override
    void apply(Project project) {

        // create an extension where the apiKey and such settings reside
        def extension = project.extensions.create("tracepotConfig", TracepotExtension, project)

        project.configure(project) {
            if (!it.hasProperty("android")) {
                return
            }

            tasks.whenTaskAdded { task ->

                project.("android").applicationVariants.all { variant ->

                    // locate packageRelease and packageDebug tasks
                    def expectingTask = "package${variant.name.capitalize()}".toString()
                    if (expectingTask.equals(task.name)) {

                        def variantName = variant.name

                        // create new task with name such as tracepotRelease and tracepotDebug
                        def newTaskName = "tracepot${variantName.capitalize()}"

                        project.task(newTaskName) << {

                            assertValidApiKey  extension
                            assertValidGroupId extension

                            if (variant.buildType.isMinifyEnabled()) {
                                String proguardMappingFilename = variant.getMappingfile().toString()

                                project.logger.debug("Using proguard mapping file at ${proguardMappingFilename}")
                                uploadMappingFile(extension, proguardMappingFilename)
                                project.logger.info("Successfully uploaded mapping file")
                            }

                            println ""
                            println "Successfully uploaded to Tracepot"
                        }

                        project.(newTaskName.toString()).dependsOn(expectingTask)
                        project.(newTaskName.toString()).group = "Tracepot"
                        project.(newTaskName.toString()).description = "Uploads an application info to Tracepot"
                    }
                }
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

    /**
     * Upload a mapping file using /api/upload-mapping REST service.
     *
     * @param extension
     * @param mappingFilename
     */
    private static void uploadMappingFile(TracepotExtension extension, String mappingFilename)
    {
        String apiEndpoint = extension.getApiEndpoint()
        String url = "${apiEndpoint}/api/upload-mapping"

        MultipartEntity entity = buildEntity(extension)

        entity.addPart('mapping_file', new FileBody(new File(mappingFilename)))

        post(url, entity)
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

        if (code != HttpStatus.SC_OK) {
            throw new GradleException("API request failed with code " + code)
        }
    }

    /**
     * Build MultipartEntity with common values
     *
     * @param extension
     * @return MultipartEntity
     */
    private static MultipartEntity buildEntity(TracepotExtension extension)
    {
        MultipartEntity entity = new MultipartEntity()

        entity.addPart('api_key',  new StringBody(extension.getApiKey()))
        entity.addPart('group_id', new StringBody(extension.getGroupId()))

        return entity
    }

}
