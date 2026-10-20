// The small mystical tables: SealedArtifacts, SpiritualSenses, Incantations,
// AntiDivinationTechniques. One Kotlin object each, a namespace each here.
import { distinctBy, eqIc, IllegalArgumentException, IllegalStateException, sumOf } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { AntiDivinationTechnique } from './content/schema.gen'

export const SealedArtifacts = {
  grades: () => ContentLoader.sealedArtifactGrades(),
}

export const SpiritualSenses = {
  all: () => ContentLoader.spiritualSenses(),
  isOffered: (isBeyonder: boolean, currentSpi: number) => isBeyonder && currentSpi >= 1,
}

export const Incantations = {
  all: () => ContentLoader.incantations(),
  standard() {
    const recited = Incantations.all().filter((it) => !it.referenceOnly)
    if (recited.length !== 1) {
      throw new IllegalStateException(
        'expected one recited incantation structure; the book prints ' + Incantations.all().map((it) => it.scope).join(', '),
      )
    }
    return recited[0]
  },
}

const distinct = (chosen: AntiDivinationTechnique[]) => distinctBy(chosen, (it) => it.id)

export const AntiDivinationTechniques = {
  all: () => ContentLoader.antiDivinationTechniques(),
  named(idOrName: string): AntiDivinationTechnique {
    const all = AntiDivinationTechniques.all()
    const t = all.find((it) => eqIc(it.id, idOrName) || eqIc(it.name, idOrName))
    if (t === undefined) {
      throw new IllegalArgumentException(
        `no anti-divination technique called '${idOrName}'; the book prints ` + all.map((it) => `${it.id} ${it.name}`).join(', '),
      )
    }
    return t
  },
  penaltyTotal: (chosen: AntiDivinationTechnique[]) => sumOf(distinct(chosen), (it) => it.divinationPenalty ?? 0),
  detectionBonus: (chosen: AntiDivinationTechnique[]) => sumOf(distinct(chosen), (it) => it.detectionBonus ?? 0),
  onDetectionRoll: () => AntiDivinationTechniques.all().filter((it) => it.detectionBonus !== null),
  spiFor: (chosen: AntiDivinationTechnique[]) => sumOf(distinct(chosen), (it) => it.spi),
}
