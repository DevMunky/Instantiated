package dev.munky.instantiated;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

/**
 * This is a required class. It must be implemented in Java.
 * Paper cannot load Kotlin classes unless the Kotlin runtime is loaded.
 * This class does that, therefore it must be written in Java.
 */
@SuppressWarnings("UnstableApiUsage") // the entire plugin loader is 'unstable'
public class InstantiatedPluginLoader implements PluginLoader {
      @Override
      public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
            RemoteRepository central = new RemoteRepository.Builder(
                  "central",
                  "default",
                  "https://repo1.maven.org/maven2/"
            ).build();
            RemoteRepository sonatype = new RemoteRepository.Builder(
                  "sonatype",
                  "default",
                  "https://oss.sonatype.org/content/groups/public/"
            ).build();
            // https://repo.codemc.io/repository/maven-public/
            RemoteRepository codemc = new RemoteRepository.Builder(
                  "codemc",
                  "default",
                  "https://repo.codemc.io/repository/maven-public/"
            ).build();
            addDependency(classpathBuilder,"org.jetbrains.kotlin:kotlin-reflect:2.0.0",central,sonatype);
            addDependency(classpathBuilder,"org.jetbrains.kotlin:kotlin-stdlib:2.0.0",central,sonatype);
            addDependency(classpathBuilder,"dev.jorel:commandapi-bukkit-shade-mojang-mapped:9.5.3",central,codemc);
            // addDependency(classpathBuilder, "io.insert-koin:koin-core:3.5.6", central); // Paper hates Koin
      }
      public void addDependency(PluginClasspathBuilder classpathBuilder, String artifact, RemoteRepository... repositories) {
            MavenLibraryResolver resolver = new MavenLibraryResolver();
            resolver.addDependency(new Dependency(new DefaultArtifact(artifact), "provided"));
            for (RemoteRepository repository : repositories) {
                  resolver.addRepository(repository);
            }
            classpathBuilder.addLibrary(resolver);
      }
}