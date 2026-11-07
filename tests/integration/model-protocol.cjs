// Adapter for our pinned, isolated integration server, not a general modded protocol implementation.
// Cobblemon PokemonItemComponent.PACKET_CODEC uses PacketCodecs.codec (anonymous NBT).
// Preserve all vanilla component codecs and decode the model's additional component as NBT.
// Tests inspect its real species/aspects; they do not replace the server's model data.
const protocol = require('minecraft-data')('1.21.1').protocol;
const component = protocol.types.SlotComponent;
const fields = Object.fromEntries(Object.entries(protocol.types.SlotComponentType[1].mappings)
  .map(([id, name]) => [id, component[1][1].type[1].fields[name]]));
module.exports = {
  SlotComponent: ['container', [
    { name: 'type', type: 'varint' },
    { name: 'data', type: ['switch', { compareTo: 'type', fields, default: 'anonymousNbt' }] }
  ]]
};
