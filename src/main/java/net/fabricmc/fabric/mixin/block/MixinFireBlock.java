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

package net.fabricmc.fabric.mixin.block;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.impl.registry.FireBlockHooks;
import net.fabricmc.fabric.impl.registry.FlammableBlockRegistryImpl;
import net.minecraft.block.Block;
import net.minecraft.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireBlock.class)
public class MixinFireBlock implements FireBlockHooks {
	private FlammableBlockRegistryImpl fabric_registry;

	@Shadow
	private int getSpreadChance(Block block_1) {
		return 0;
	}

	@Shadow
	private int getBurnChance(Block block_1) {
		return 0;
	}

	@Inject(at = @At("RETURN"), method = "<init>")
	private void afterConstruct(Block.Settings settings, CallbackInfo info) {
		fabric_registry = FlammableBlockRegistryImpl.getInstance((Block) (Object) this);
	}

	@Inject(at = @At("HEAD"), method = "getBurnChance", cancellable = true)
	private void getFabricBurnChance(Block block, CallbackInfoReturnable info) {
		FlammableBlockRegistry.Entry entry = fabric_registry.getFabric(block);
		if (entry != null) {
			info.setReturnValue(entry.getBurnChance());
			info.cancel();
		}
	}

	@Inject(at = @At("HEAD"), method = "getSpreadChance", cancellable = true)
	private void getFabricSpreadChance(Block block, CallbackInfoReturnable info) {
		FlammableBlockRegistry.Entry entry = fabric_registry.getFabric(block);
		if (entry != null) {
			info.setReturnValue(entry.getSpreadChance());
			info.cancel();
		}
	}

	@Override
	public FlammableBlockRegistry.Entry fabric_getVanillaEntry(Block block) {
		return new FlammableBlockRegistry.Entry(getBurnChance(block), getSpreadChance(block));
	}
}
