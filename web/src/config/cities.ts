/**
 * Suggested places of birth for the city picker.
 *
 * Azerbaijan's cities of republic significance and rayon centres — the answer
 * for almost every employee on this register. There is no canonical worldwide
 * city list to pick from, so the control accepts a typed value as well: an
 * expat born in Milan or Aberdeen is not blocked by a list that could never
 * cover them. Treat this as suggestions that keep the common case spelled
 * consistently, not as a closed set.
 */
export const AZ_CITIES: readonly string[] = [
  // Cities of republic significance
  'Baku', 'Ganja', 'Sumqayit', 'Mingachevir', 'Lankaran', 'Shirvan',
  'Naftalan', 'Shaki', 'Yevlakh', 'Khankendi', 'Nakhchivan',
  // Rayon centres
  'Absheron', 'Aghdam', 'Aghdash', 'Aghjabadi', 'Aghstafa', 'Aghsu',
  'Astara', 'Babek', 'Balakan', 'Barda', 'Beylagan', 'Bilasuvar',
  'Dashkasan', 'Fuzuli', 'Gadabay', 'Goranboy', 'Goychay', 'Goygol',
  'Hajigabul', 'Imishli', 'Ismayilli', 'Jabrayil', 'Jalilabad', 'Julfa',
  'Kalbajar', 'Kangarli', 'Khachmaz', 'Khizi', 'Khojaly', 'Khojavend',
  'Kurdamir', 'Lachin', 'Lerik', 'Masally', 'Neftchala', 'Oghuz',
  'Ordubad', 'Qabala', 'Qakh', 'Qazakh', 'Qobustan', 'Quba', 'Qubadli',
  'Qusar', 'Saatly', 'Sabirabad', 'Sadarak', 'Salyan', 'Samukh',
  'Shabran', 'Shahbuz', 'Shamakhi', 'Shamkir', 'Sharur', 'Shusha',
  'Siyazan', 'Tartar', 'Tovuz', 'Ujar', 'Yardimli', 'Zangilan',
  'Zaqatala', 'Zardab',
]

export const CITY_OPTIONS = AZ_CITIES.map((c) => ({ value: c, label: c }))
