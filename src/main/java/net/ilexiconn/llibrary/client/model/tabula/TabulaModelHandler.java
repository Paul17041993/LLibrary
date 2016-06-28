package net.ilexiconn.llibrary.client.model.tabula;

import com.google.common.collect.ImmutableList;
import com.google.gson.*;
import net.ilexiconn.llibrary.client.model.tabula.baked.VanillaTabulaModel;
import net.ilexiconn.llibrary.client.model.tabula.baked.deserializer.ItemCameraTransformsDeserializer;
import net.ilexiconn.llibrary.client.model.tabula.baked.deserializer.ItemTransformVec3fDeserializer;
import net.ilexiconn.llibrary.client.model.tabula.container.TabulaCubeContainer;
import net.ilexiconn.llibrary.client.model.tabula.container.TabulaCubeGroupContainer;
import net.ilexiconn.llibrary.client.model.tabula.container.TabulaModelContainer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.IPerspectiveAwareModel;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author pau101
 * @since 1.0.0
 */
@SideOnly(Side.CLIENT)
public enum TabulaModelHandler implements ICustomModelLoader, JsonDeserializationContext {
    INSTANCE;

    private Gson gson = new GsonBuilder().registerTypeAdapter(ItemTransformVec3f.class, new ItemTransformVec3fDeserializer()).registerTypeAdapter(ItemCameraTransforms.class, new ItemCameraTransformsDeserializer()).create();
    private JsonParser parser = new JsonParser();
    private ModelBlock.Deserializer modelBlockDeserializer = new ModelBlock.Deserializer();
    private IResourceManager manager;

    /**
     * Load a {@link TabulaModelContainer} from the path. A slash will be added if it isn't in the path already.
     *
     * @param path the model path
     * @return the new {@link TabulaModelContainer} instance
     * @throws IOException if the file can't be found
     */
    public TabulaModelContainer loadTabulaModel(String path) throws IOException {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith(".tbl")) {
            path += ".tbl";
        }
        InputStream stream = TabulaModelHandler.class.getResourceAsStream(path);
        return TabulaModelHandler.INSTANCE.loadTabulaModel(this.getModelJsonStream(path, stream));
    }

    /**
     * Load a {@link TabulaModelContainer} from the model.json input stream.
     *
     * @param stream the model.json input stream
     * @return the new {@link TabulaModelContainer} instance
     */
    public TabulaModelContainer loadTabulaModel(InputStream stream) {
        return gson.fromJson(new InputStreamReader(stream), TabulaModelContainer.class);
    }

    /**
     * @param name  the cube name
     * @param model the model container
     * @return the cube
     */
    public TabulaCubeContainer getCubeByName(String name, TabulaModelContainer model) {
        List<TabulaCubeContainer> allCubes = getAllCubes(model);

        for (TabulaCubeContainer cube : allCubes) {
            if (cube.getName().equals(name)) {
                return cube;
            }
        }

        return null;
    }

    /**
     * @param identifier the cube identifier
     * @param model      the model container
     * @return the cube
     */
    public TabulaCubeContainer getCubeByIdentifier(String identifier, TabulaModelContainer model) {
        List<TabulaCubeContainer> allCubes = getAllCubes(model);

        for (TabulaCubeContainer cube : allCubes) {
            if (cube.getIdentifier().equals(identifier)) {
                return cube;
            }
        }

        return null;
    }

    /**
     * @param model the model container
     * @return an array with all cubes of the model
     */
    public List<TabulaCubeContainer> getAllCubes(TabulaModelContainer model) {
        List<TabulaCubeContainer> cubes = new ArrayList<>();

        for (TabulaCubeGroupContainer cubeGroup : model.getCubeGroups()) {
            cubes.addAll(traverse(cubeGroup));
        }

        for (TabulaCubeContainer cube : model.getCubes()) {
            cubes.addAll(traverse(cube));
        }

        return cubes;
    }

    private List<TabulaCubeContainer> traverse(TabulaCubeGroupContainer group) {
        List<TabulaCubeContainer> retCubes = new ArrayList<>();

        for (TabulaCubeContainer child : group.getCubes()) {
            retCubes.addAll(traverse(child));
        }

        for (TabulaCubeGroupContainer child : group.getCubeGroups()) {
            retCubes.addAll(traverse(child));
        }

        return retCubes;
    }

    private List<TabulaCubeContainer> traverse(TabulaCubeContainer cube) {
        List<TabulaCubeContainer> retCubes = new ArrayList<>();

        retCubes.add(cube);

        for (TabulaCubeContainer child : cube.getChildren()) {
            retCubes.addAll(traverse(child));
        }

        return retCubes;
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        return modelLocation.getResourcePath().endsWith(".tbl");
    }

    @Override
    public IModel loadModel(ResourceLocation modelLocation) throws IOException {
        String modelPath = modelLocation.getResourcePath();
        modelPath = modelPath.substring(0, modelPath.lastIndexOf('.')) + ".json";
        IResource resource = this.manager.getResource(new ResourceLocation(modelLocation.getResourceDomain(), modelPath));
        InputStreamReader jsonStream = new InputStreamReader(resource.getInputStream());
        JsonElement json = this.parser.parse(jsonStream);
        jsonStream.close();
        ModelBlock modelBlock = this.modelBlockDeserializer.deserialize(json, ModelBlock.class, this);
        String tblLocationStr = json.getAsJsonObject().get("tabula").getAsString() + ".tbl";
        ResourceLocation tblLocation = new ResourceLocation(tblLocationStr);
        IResource tblResource = this.manager.getResource(tblLocation);
        InputStream modelStream = getModelJsonStream(tblLocation.toString(), tblResource.getInputStream());
        TabulaModelContainer modelJson = TabulaModelHandler.INSTANCE.loadTabulaModel(modelStream);
        modelStream.close();
        ImmutableList.Builder<ResourceLocation> builder = ImmutableList.builder();
        int layer = 0;
        String texture;
        while ((texture = modelBlock.textures.get("layer" + layer++)) != null) {
            builder.add(new ResourceLocation(texture));
        }
        return new VanillaTabulaModel(modelJson, builder.build(), IPerspectiveAwareModel.MapWrapper.getTransforms(modelBlock.getAllTransforms()));
    }

    private InputStream getModelJsonStream(String name, InputStream file) throws IOException {
        ZipInputStream zip = new ZipInputStream(file);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.getName().equals("model.json")) {
                return zip;
            }
        }
        throw new RuntimeException("No model.json present in " + name);
    }

    @Override
    public <T> T deserialize(JsonElement json, Type type) throws JsonParseException {
        return gson.fromJson(json, type);
    }
}
