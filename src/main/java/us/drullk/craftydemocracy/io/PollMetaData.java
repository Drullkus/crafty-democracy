package us.drullk.craftydemocracy.io;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record PollMetaData(String name, int choiceLimit, List<String> choices) {

	public static final Codec<PollMetaData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("name").forGetter(PollMetaData::name),
			Codec.INT.fieldOf("choice_limit").forGetter(PollMetaData::choiceLimit),
			Codec.STRING.listOf().fieldOf("choices").forGetter(PollMetaData::choices)
	).apply(inst, PollMetaData::new));

}
