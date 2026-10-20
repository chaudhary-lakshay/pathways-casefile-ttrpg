// What the app does not apply of a held trait, row by row, with the reason.
import { lazy } from './kt'
import * as Languages from './Languages'
import {
  ACUTE_HEARING, ACUTE_VISION, BECKONING_LUCK, CHARISMA, CHRONIC_INSOMNIA, CLEANSED_SPIRIT, ETHER_BODY_DAMAGE,
  FAST_HEALER, FATE_DEBT, GREAT_BALANCE, IRON_JAW, LUCK, LUCK_REACH, PERFECT_BALANCE, RAPID_RECOVERY,
  RELUCTANT_KILLER, THINNED_VEIL, UNFIT, VERY_FIT,
} from './TraitEffects'

const GM = 'the book hands this to the GM, not to a roll'
const NO_SESSION = 'this app keeps no session, so the GM counts them'

const PURPOSE_NOT_SKILL =
  'this says what a roll is for and names no skill, nor a group the ' +
  "book lists, so which rolls count is the GM's call"
const CLOSE_QUARTERS_UNPRINTED = 'this book prints no close-quarters penalty for it to ignore'
const KNOCKDOWN_UNREACHED =
  "the book's knockdown rolls for a hit location print no attribute to " +
  'roll against, so this app shows those cells rather than rolling them'

export const PARTLY_APPLIED = lazy(() => new Map<string, [string, string][]>([
  ['Light Sleeper', [
    ['never caught completely unaware at night',
      "surprise is the GM's to declare on the Defend dialog, and nothing here decides it"],
  ]],
  [Languages.MULTILINGUAL, [
    ['No penalty to language-dependent social skills in those languages',
      'this book tags no skill as language-dependent, so which rolls ' +
      "that reaches is the GM's call"],
  ]],
  [THINNED_VEIL, [
    ['Concentration in spiritually active environments at -2',
      'beyond the Concentrate roll, where it is declared; which other ' +
      "rolls need concentration, a ritual's included, is the GM's call"],
  ]],
  [CHRONIC_INSOMNIA, [
    ['Lose 1 FP each morning that cannot be recovered through rest',
      'this app counts no mornings, so the table lowers FP on the FP row, and keeps it from rest'],
    ['rolls requiring sustained concentration at -1',
      'beyond the Concentrate roll, where it is declared; which other ' +
      "rolls need sustained concentration, a ritual's included, is the GM's call"],
  ]],
  [LUCK, [
    ['1 reroll per session', NO_SESSION],
    ['may reroll any one failed roll', LUCK_REACH],
  ]],
  [BECKONING_LUCK, [
    ['2 rerolls per session', NO_SESSION],
    ['reroll any failed roll, usable at any time', LUCK_REACH],
    ['and fate may balance later', GM],
  ]],
  [FATE_DEBT, [
    ['You owe fate something', GM],
    ['The GM holds a narrative debt to be called in at a dramatically significant moment', GM],
  ]],
  ['Class Mobility', [
    ['Move believably across two social classes', GM],
    ['NPCs rarely question your presence', GM],
  ]],
  ['Fearsome Reputation', [
    ['Known in criminal circles', GM],
    ['Relevant NPCs start cautious', GM],
  ]],
  ['Alcohol Tolerance', [
    ['Never suffer social penalties from moderate drinking',
      'this app applies no drinking penalty, so there is none to lift'],
  ]],
  ["Navigator's Eye", [
    ['Never become lost in any city previously visited', GM],
  ]],
  [ACUTE_VISION, [
    ['notice details at a distance, read lips, spot hidden objects',
      'says what a Vision roll is for; on a Per roll the GM declares whether this is one'],
  ]],
  [ACUTE_HEARING, [
    ['detect faint sounds, eavesdrop through walls, identify speech in noise',
      'says what a Hearing roll is for; on a Per roll the GM declares whether this is one'],
  ]],
  ['Famous Face', [
    ['Recognizable in the city', GM],
    ['surveillance harder', "the book prints no number for it, so it is the GM's"],
    ['strangers approach you, sometimes dangerously', GM],
  ]],
  [FAST_HEALER, [
    ['Injuries that would leave others bedridden leave you functional in half the time',
      'this app tracks no wounds and holds no clock to halve'],
  ]],
  [RELUCTANT_KILLER, [
    ['become morose for 3d days', 'this app holds no clock to count the days against'],
  ]],
  [VERY_FIT, [
    ['recover FP at twice the normal rate', 'this app holds no clock to recover against'],
  ]],
  [PERFECT_BALANCE, [['+6 to avoid knockdown', KNOCKDOWN_UNREACHED]]],
  [GREAT_BALANCE, [['+2 to avoid knockdown', KNOCKDOWN_UNREACHED]]],
  ['Fearlessness', [
    ['immunity to intimidation from beings with fewer levels of Fearlessness',
      "that is a contest between two characters' levels, and this app holds one sheet"],
  ]],
  ['Flexibility', [['ignore up to -3 close-quarters penalties', CLOSE_QUARTERS_UNPRINTED]]],
  ['Double-Jointed', [
    ['ignore up to -5 close-quarters penalties', CLOSE_QUARTERS_UNPRINTED],
    ['any body part bends any way', 'a capability rather than a roll, so the GM adjudicates it'],
  ]],
  ['Absent-Mindedness', [
    ['In combat, may forget to reload, change tactics, or use special abilities (GM\'s discretion)',
      "the cell leaves it to the GM's discretion in its own words"],
  ]],
  ['Entity Fixation', [['-2 to any roll that requires you to ignore them', PURPOSE_NOT_SKILL]]],
  ['Healer', [
    ['+2 to all rolls to diagnose, treat, and heal', PURPOSE_NOT_SKILL],
    ['HT rolls to avoid or recover from disease at +2', 'this app models no disease roll'],
  ]],
  ['Absolute Direction', [
    ['Always know which way is north; never become lost in natural terrain',
      'the book hands both to the GM, not to a roll'],
  ]],
  ['Former Military Officer', [
    ['military contacts', "this app keeps no contacts, and who they are is the GM's"],
    ['entitled to officer courtesies in formal settings', GM],
  ]],
  ['Photographic Instinct', [['instinctively know what to capture as evidence', GM]]],
  ['Sanctity', [
    ['once per session, the GM must answer one factual question about Church doctrine or history honestly',
      "this app holds no session to count, and the answer is the GM's"],
  ]],
  [CHARISMA, [['NPCs are predisposed to trust and listen to you', GM]]],
  [UNFIT, [['recover FP at half normal rate', 'this app holds no clock to recover against']]],
  [ETHER_BODY_DAMAGE, [
    ['Spirit Vision on you reveals the damage immediately',
      "that is what another character's Spirit Vision sees, and this app holds one sheet"],
  ]],
  ['Local Legend', [['people look out for you', GM]]],
  ['Illegitimate Birth', [['inheritance and legal rights are complicated', GM]]],
  ['Dead-Eyed', [
    ['animals are skittish around you', GM],
    ['children sometimes cry', GM],
  ]],
]))

export const UNAPPLIED = lazy(() => new Map<string, string>([
  ['Danger Sense', GM],
  ['Voice', PURPOSE_NOT_SKILL],
  ['Industrial Expertise', PURPOSE_NOT_SKILL],
  ['Anchored Soul',
    "the -2 is on the attempt, the other party's roll, and this app " +
    'holds one sheet; where the holder is the one who rolls ("The ' +
    'target rolls Will-6 to resist", a potion drawback\'s self-control ' +
    'roll) the book prints no rule turning it into a bonus on that ' +
    "roll, so that is the GM's call"],
  [CLEANSED_SPIRIT,
    'the book lists no CoR source as passive exposure, so this app ' +
    'halves nothing it charges, Soul Debt from SPI spent below 0 ' +
    'included; the GM halves what is passive with the CoR stepper'],
  ['Official Beyonder',
    'the book does not say whether its four "+N" entries are ' +
    'bonuses to a roll or skills granted at that level, so none is ' +
    'counted; the language, the Legal Enforcement Powers and the ' +
    'Duty are not added to this sheet either'],
  [Languages.ROW,
    'the -2 and -3 are "to language-dependent skill rolls", and this ' +
    'book tags no skill as language-dependent, so which rolls they ' +
    "touch is the GM's call; a ritual's language is picked in the " +
    'Ritual dialog'],
  ['Eidetic Memory', 'this app models no recall roll'],
  ['Single-Minded', "which tasks count as extended concentration is the GM's call"],
  ['Lightning Calculator', 'this app models no numerical-puzzle roll'],
  [IRON_JAW, 'this app rolls no knockdown for a hit location, and no bite'],
  ['Goddess of Evernight',
    '"most divination" prints no number, and being divined against ' +
    "is the other party's roll — this app holds one sheet"],
  ['Lord of Storms',
    'the +3 is scoped to pressure, and this app rolls no pressure ' +
    'roll; the ten hours are a duration it holds no clock for'],
  ['Earth Mother',
    "reviving a DEAD character is not a roll — the HP table's own two " +
    'rolls stop at "-5xHP: Dead, no roll required" — and burying to ' +
    'regenerate is healing over time, which needs a clock'],
  ['Eternal Blazing Sun', 'the 1d6 falls on someone else each round, and this app holds one sheet'],
  ['God of Steam & Machinery', 'identification and negated curses print no number to apply'],
  [RAPID_RECOVERY, 'both halves are durations, and this app holds no clock'],
  ['Natural Leader',
    'this app models no morale roll, and the reroll it grants belongs ' +
    "to an ally's Fright Check rather than to this character's"],
  ['Uncanny Survivor',
    'the death roll now exists, but "once per campaign arc" is a ' +
    'budget this app holds no campaign to spend from, and the book ' +
    'hands the cost to the GM'],
  ['Glass Jaw',
    "the book's knockdown rolls for a hit location print no attribute " +
    'to roll against, so this app shows those cells rather than rolling ' +
    'them — the refusal Iron Jaw carries, with the sign flipped'],
  ['Resistant (specify)',
    'the bonus is scoped to a category the GM names (Disease, Poison, ' +
    'Temperature Extremes), and this app offers no roll to resist any ' +
    'of them'],
  ['Duty (specify)',
    'its "roll 9 or less" is how often the duty comes up, the GM\'s ' +
    'to roll, rather than a roll this character makes'],
]))
