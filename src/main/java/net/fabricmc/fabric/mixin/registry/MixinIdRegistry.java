/*
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.fabric.mixin.registry;

import com.google.common.collect.BiMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.impl.registry.ListenableRegistry;
import net.fabricmc.fabric.impl.registry.RegistryListener;
import net.fabricmc.fabric.impl.registry.RemapException;
import net.fabricmc.fabric.impl.registry.RemappableRegistry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Int2ObjectBiMap;
import net.minecraft.util.registry.DefaultedRegistry;
import net.minecraft.util.registry.SimpleRegistry;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(SimpleRegistry.class)
public abstract class MixinIdRegistry<T> implements RemappableRegistry, ListenableRegistry<T>, RegistryListener<T> {
	@Shadow
	protected static Logger LOGGER;
	@Shadow
	protected Int2ObjectBiMap<T> indexedEntries;
	@Shadow
	protected BiMap<Identifier, T> entries;
	@Shadow
	private int nextId;

	private Object2IntMap<Identifier> initialIdMap;
	private RegistryListener[] listeners;

	@Override
	public void registerListener(RegistryListener<T> listener) {
		if (listeners == null) {
			listeners = new RegistryListener[] { listener };
		} else {
			RegistryListener[] newListeners = new RegistryListener[listeners.length + 1];
			System.arraycopy(listeners, 0, newListeners, 0, listeners.length);
			newListeners[listeners.length] = listener;
			listeners = newListeners;
		}
	}

	@SuppressWarnings({ "unchecked", "ConstantConditions" })
	@Inject(method = "set", at = @At("HEAD"))
	public void setPre(int id, Identifier identifier, Object object, CallbackInfoReturnable info) {
		SimpleRegistry<Object> registry = (SimpleRegistry<Object>) (Object) this;
		if (listeners != null) {
			for (RegistryListener listener : listeners) {
				listener.beforeRegistryRegistration(registry, id, identifier, object, !entries.containsKey(identifier));
			}
		}
	}

	@SuppressWarnings({ "unchecked", "ConstantConditions" })
	@Inject(method = "set", at = @At("RETURN"))
	public void setPost(int id, Identifier identifier, Object object, CallbackInfoReturnable info) {
		SimpleRegistry<Object> registry = (SimpleRegistry<Object>) (Object) this;
		if (listeners != null) {
			for (RegistryListener listener : listeners) {
				listener.afterRegistryRegistration(registry, id, identifier, object);
			}
		}
	}

	@Override
	public void remap(Object2IntMap<Identifier> idMap, boolean reallocateMissingEntries) throws RemapException {
		//noinspection unchecked, ConstantConditions
		SimpleRegistry<Object> registry = (SimpleRegistry<Object>) (Object) this;

		Object defaultValue = null;
		//noinspection ConstantConditions
		if (registry instanceof DefaultedRegistry) {
			defaultValue = registry.get(((DefaultedRegistry<Object>) registry).getDefaultId());
		}

		if (!reallocateMissingEntries && !idMap.keySet().equals(registry.getIds())) {
			throw new RemapException("Source and destination keys differ!");
		}

		if (initialIdMap == null) {
			initialIdMap = new Object2IntOpenHashMap<>();
			for (Identifier id : registry.getIds()) {
				//noinspection unchecked
				initialIdMap.put(id, registry.getRawId(registry.get(id)));
			}
		}

		if (reallocateMissingEntries) {
			int maxValue = 0;

			Object2IntMap<Identifier> idMapOld = idMap;
			idMap = new Object2IntOpenHashMap<>();
			for (Identifier id : idMapOld.keySet()) {
				int v = idMapOld.getInt(id);
				idMap.put(id, v);
				if (v > maxValue) maxValue = v;
			}

			for (Identifier id : registry.getIds()) {
				if (!idMap.containsKey(id)) {
					LOGGER.warn("Adding " + id + " to registry.");
					idMap.put(id, ++maxValue);
				}
			}
		}

		if (listeners != null) {
			for (RegistryListener listener : listeners) {
				listener.beforeRegistryCleared(registry);
			}
		}

		// We don't really need to clear anything but indexedEntries yet.
		indexedEntries.clear();
		nextId = 0;

		List<Identifier> idsInOrder = new ArrayList<>(idMap.keySet());
		idsInOrder.sort(Comparator.comparingInt(idMap::getInt));

		for (Identifier identifier : idsInOrder) {
			int id = idMap.getInt(identifier);
			T object = entries.get(identifier);
			if (object == null) {
				LOGGER.warn(identifier + " missing from registry, but requested!");
				continue;
				
				//noinspection unchecked, ConstantConditions
				// object = (T) defaultValue;
				// entries.put(identifier, object);
			}

			indexedEntries.put(object, id);
			if (nextId <= id) {
				nextId = id + 1;
			}

			if (listeners != null) {
				for (RegistryListener listener : listeners) {
					listener.beforeRegistryRegistration(registry, id, identifier, object, false);
				}
			}
		}
	}

	@Override
	public void unmap() throws RemapException {
		if (initialIdMap != null) {
			remap(initialIdMap, true);
			initialIdMap = null;
		}
	}
}
