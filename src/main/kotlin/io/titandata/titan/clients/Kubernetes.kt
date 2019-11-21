/*
 * Copyright (c) 2019 by Delphix. All rights reserved.
 */

package io.titandata.titan.clients

import io.kubernetes.client.ApiException
import io.kubernetes.client.Configuration
import io.kubernetes.client.apis.AppsV1Api
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.models.V1ContainerBuilder
import io.kubernetes.client.models.V1ContainerPortBuilder
import io.kubernetes.client.models.V1LabelSelectorBuilder
import io.kubernetes.client.models.V1ObjectMetaBuilder
import io.kubernetes.client.models.V1PersistentVolumeClaimBuilder
import io.kubernetes.client.models.V1PersistentVolumeClaimSpec
import io.kubernetes.client.models.V1PersistentVolumeClaimSpecBuilder
import io.kubernetes.client.models.V1PodSpecBuilder
import io.kubernetes.client.models.V1PodTemplateBuilder
import io.kubernetes.client.models.V1PodTemplateSpecBuilder
import io.kubernetes.client.models.V1ResourceRequirementsBuilder
import io.kubernetes.client.models.V1ServiceBuilder
import io.kubernetes.client.models.V1ServicePortBuilder
import io.kubernetes.client.models.V1ServiceSpecBuilder
import io.kubernetes.client.models.V1StatefulSet
import io.kubernetes.client.models.V1StatefulSetBuilder
import io.kubernetes.client.models.V1StatefulSetSpecBuilder
import io.kubernetes.client.models.V1VolumeMountBuilder
import io.kubernetes.client.util.Config
import io.titandata.models.Volume
import io.titandata.titan.Version
import io.titandata.titan.Version.Companion.compare
import io.titandata.titan.utils.CommandExecutor
import org.json.JSONArray
import org.json.JSONObject
import org.kohsuke.randname.RandomNameGenerator
import kotlin.random.Random

class Kubernetes() {
    private val executor = CommandExecutor()
    private var coreApi: CoreV1Api
    private var appsApi: AppsV1Api
    private val defaultNamespace = "default"

    init {
        val client = Config.defaultClient()
        Configuration.setDefaultApiClient(client)
        coreApi = CoreV1Api()
        appsApi = AppsV1Api()
    }

    /**
     * For our repositories, we keep it very simple. There is a single headless service that is responsible for exposing
     * the ports in the container. We then create a single replica stateful set with the given volumes (each with
     * existing PVCs) mapped in.
     */
    fun createStatefulSet(repoName: String, imageId: String, ports: List<Int>, volumes: List<Volume>) {
        val metadata = V1ObjectMetaBuilder()
                .withName(repoName)
                .withLabels(mapOf("titanRepository" to repoName))
                .build()

        coreApi.createNamespacedService(defaultNamespace, V1ServiceBuilder()
                .withMetadata(metadata)
                .withSpec(V1ServiceSpecBuilder()
                        .withClusterIP("None")
                        .withSelector(mapOf("titanRepository" to repoName))
                        .withPorts(
                                ports.map { V1ServicePortBuilder().withPort(it).withName("port-$it").build() })
                        .build())
                .build(), null, null, null)

        appsApi.createNamespacedStatefulSet(defaultNamespace, V1StatefulSetBuilder()
                .withMetadata(metadata)
                .withSpec(V1StatefulSetSpecBuilder()
                        .withReplicas(1)
                        .withServiceName(repoName)
                        .withSelector(V1LabelSelectorBuilder().withMatchLabels(mapOf("titanRepository" to repoName)).build())
                        .withTemplate(V1PodTemplateSpecBuilder()
                                .withMetadata(metadata)
                                .withSpec(V1PodSpecBuilder()
                                        .withContainers(V1ContainerBuilder()
                                                .withName(repoName)
                                                .withImage(imageId)
                                                .withPorts(ports.map { V1ContainerPortBuilder().withContainerPort(it).withName("port-$it").build() })
                                                .withVolumeMounts(volumes.map { V1VolumeMountBuilder().withName(it.name).withMountPath(it.properties["path"] as String).build()})
                                                .build())
                                        .build())
                                .build())
                        .withVolumeClaimTemplates(volumes.map {
                            V1PersistentVolumeClaimBuilder()
                                    .withMetadata(V1ObjectMetaBuilder()
                                            .withName(it.name)
                                            .withLabels(mapOf("titanRepository" to repoName))
                                            .build())
                                    .withSpec(V1PersistentVolumeClaimSpecBuilder()
                                            .withAccessModes("ReadWriteOnce")
                                            .withResources(V1ResourceRequirementsBuilder()
                                                    .withRequests(mapOf("storage" to Quantity.fromString(it.config["size"] as String)))
                                                    .build())
                                            .build())
                                    .build()} )
                            .build())
                            .build(), null, null, null)
    }

    fun deleteStatefulSpec(repoName: String) {
        try {
            appsApi.deleteNamespacedStatefulSet(repoName, defaultNamespace, null, null, null, 0, null, null)
        } catch (e: ApiException) {
            if (e.code != 404) {
                throw e
            }
        }
        try {
            coreApi.deleteNamespacedService(repoName, defaultNamespace, null, null, null, 0, null, null)
        } catch (e: ApiException) {
            if (e.code != 404) {
                throw e
            }
        }
    }

    fun waitForStatefulSet(repoName: String) {
        while (true) {
            var set = appsApi.readNamespacedStatefulSet(repoName, defaultNamespace, null, null, null)
            // TODO detect fatal conditions that will cause it to never reach readiness
            if (set.status.readyReplicas == set.status.replicas) {
                break
            }
            Thread.sleep(1000L)
        }
    }

    /**
     * Forward port for a container. For now, we're using a temporary solution of launching 'kubectl-forward' in the
     * background. This is totally brittle, as the commands will fail in the background as pods are stopped and
     * connections broken. And if you restart the host system, there is no way to restart them. But it's a quick
     * hack to demonstrate the desired experience until we can build out a more full-featured port forwarder, such
     * as: https://github.com/pixel-point/kube-forwarder
     */
    fun forwardPorts(repoName: String, ports: List<Int>) {
        for (port in ports) {
            executor.exec(listOf("sh", "-c", "kubectl port-forward svc/$repoName $port > /dev/null 2>&1 &"))
        }
    }

}