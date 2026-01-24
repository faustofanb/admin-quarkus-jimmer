import org.sonatype.nexus.repository.config.Configuration
import org.sonatype.nexus.repository.storage.WritePolicy

// Configuration helpers
def createMavenHosted(name, policy) {
    if (repository.getRepositoryManager().get(name) == null) {
        repository.createMavenHosted(name, 'default', true, policy, WritePolicy.ALLOW_ONCE)
    }
}

def createMavenProxy(name, remoteUrl) {
    if (repository.getRepositoryManager().get(name) == null) {
        repository.createMavenProxy(name, remoteUrl, 'default', true, org.sonatype.nexus.repository.maven.LayoutPolicy.STRICT, WritePolicy.ALLOW_ONCE)
    }
}

def createMavenGroup(name, members) {
    if (repository.getRepositoryManager().get(name) == null) {
        repository.createMavenGroup(name, members, 'default')
    }
}

def createDockerHosted(name, httpPort) {
    if (repository.getRepositoryManager().get(name) == null) {
        def conf = new Configuration(
            repositoryName: name,
            recipeName: 'docker-hosted',
            online: true,
            attributes: [
                docker: [
                    v1Enabled: false,
                    forceBasicAuth: true,
                    httpPort: httpPort
                ]
            ]
        )
        repository.getRepositoryManager().create(conf)
    }
}

def createDockerProxy(name, remoteUrl) {
    if (repository.getRepositoryManager().get(name) == null) {
         def conf = new Configuration(
            repositoryName: name,
            recipeName: 'docker-proxy',
            online: true,
            attributes: [
                docker: [
                    v1Enabled: false,
                    forceBasicAuth: true
                ],
                proxy: [
                    remoteUrl: remoteUrl,
                    contentMaxAge: 1440.0,
                    metadataMaxAge: 1440.0
                ],
                httpclient: [
                    blocked: false,
                    autoBlock: true
                ]
            ]
        )
        repository.getRepositoryManager().create(conf)
    }
}

def createDockerGroup(name, httpPort, members) {
    if (repository.getRepositoryManager().get(name) == null) {
         def conf = new Configuration(
            repositoryName: name,
            recipeName: 'docker-group',
            online: true,
            attributes: [
                docker: [
                    v1Enabled: false,
                    forceBasicAuth: true,
                    httpPort: httpPort
                ],
                group: [
                    memberNames: members
                ]
            ]
        )
        repository.getRepositoryManager().create(conf)
    }
}

// --- Execution ---

// Maven
createMavenHosted('maven-hosted', org.sonatype.nexus.repository.maven.VersionPolicy.RELEASE)
createMavenProxy('maven-proxy', 'https://repo1.maven.org/maven2/')
createMavenGroup('maven-group', ['maven-hosted', 'maven-proxy'])

// Docker
// Port 5001 for Hosted (Push)
createDockerHosted('docker-hosted', 5001)
// Proxy Docker Hub
createDockerProxy('docker-proxy', 'https://registry-1.docker.io')
// Port 5000 for Group (Pull)
createDockerGroup('docker-group', 5000, ['docker-hosted', 'docker-proxy'])

log.info('Script initialization completed.')
