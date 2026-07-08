import { NewsArticleData } from '../components/NewsArticle'

export const mockNews: NewsArticleData[] = [
  {
    id: '1',
    title: 'Apple Unveils New AI Features for iPhone 16, Stock Surges 3%',
    summary:
      'Apple Inc. announced breakthrough AI capabilities integrated into the latest iPhone 16 series, sending shares higher in early trading as investors bet on a strong upgrade cycle.',
    source: 'Bloomberg',
    sourceUrl: 'https://www.bloomberg.com',
    time: '2 hours ago',
    tickers: ['AAPL'],
    category: 'Technology',
    imageUrl:
      'https://images.unsplash.com/photo-1611472173362-3f53dbd65d60?w=400&h=300&fit=crop',
  },
  {
    id: '2',
    title: 'Federal Reserve Signals Potential Rate Cut in November Meeting',
    summary:
      'Fed Chair Jerome Powell hinted at a possible interest rate reduction following positive inflation data, marking a significant shift in monetary policy stance.',
    source: 'Reuters',
    sourceUrl: 'https://www.reuters.com',
    time: '4 hours ago',
    tickers: ['SPX', 'DJIA'],
    category: 'Economics',
  },
  {
    id: '3',
    title: 'Tesla Reports Record Q3 Deliveries, Beating Analyst Estimates',
    summary:
      'Electric vehicle maker Tesla delivered 462,000 vehicles in the third quarter, exceeding Wall Street expectations and alleviating concerns about softening demand.',
    source: 'CNBC',
    sourceUrl: 'https://www.cnbc.com',
    time: '5 hours ago',
    tickers: ['TSLA'],
    category: 'Automotive',
    imageUrl:
      'https://images.unsplash.com/photo-1560958089-b8a1929cea89?w=400&h=300&fit=crop',
  },
  {
    id: '4',
    title: 'Microsoft and OpenAI Partnership Deepens with $10B Investment',
    summary:
      'Microsoft announces additional funding for OpenAI as the tech giant doubles down on artificial intelligence, positioning itself as a leader in the AI revolution.',
    source: 'Wall Street Journal',
    sourceUrl: 'https://www.wsj.com',
    time: '6 hours ago',
    tickers: ['MSFT'],
    category: 'Technology',
  },
  {
    id: '5',
    title: 'Oil Prices Jump on OPEC+ Production Cut Extension',
    summary:
      'Crude oil futures rallied nearly 3% after OPEC+ members agreed to extend production cuts through the end of the year, tightening global supply.',
    source: 'Financial Times',
    sourceUrl: 'https://www.ft.com',
    time: '7 hours ago',
    tickers: ['CL', 'XOM', 'CVX'],
    category: 'Energy',
    imageUrl:
      'https://images.unsplash.com/photo-1566228015668-4c45dbc4e2f5?w=400&h=300&fit=crop',
  },
  {
    id: '6',
    title: 'Amazon Expands Same-Day Delivery to 50 More Cities',
    summary:
      'E-commerce giant Amazon continues its logistics expansion, bringing same-day delivery services to millions of additional customers ahead of the holiday season.',
    source: 'Bloomberg',
    sourceUrl: 'https://www.bloomberg.com',
    time: '8 hours ago',
    tickers: ['AMZN'],
    category: 'Retail',
  },
  {
    id: '7',
    title: 'JPMorgan Chase Raises GDP Forecast Amid Strong Consumer Spending',
    summary:
      "The nation's largest bank increased its economic growth projections, citing resilient consumer spending and a robust labor market despite higher interest rates.",
    source: 'Reuters',
    sourceUrl: 'https://www.reuters.com',
    time: '9 hours ago',
    tickers: ['JPM', 'SPX'],
    category: 'Banking',
    imageUrl:
      'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=400&h=300&fit=crop',
  },
  {
    id: '8',
    title: 'Nvidia Announces Next-Generation AI Chips for Data Centers',
    summary:
      'Graphics chip maker Nvidia unveiled its latest GPU architecture designed for AI workloads, maintaining its dominance in the rapidly growing artificial intelligence chip market.',
    source: 'CNBC',
    sourceUrl: 'https://www.cnbc.com',
    time: '10 hours ago',
    tickers: ['NVDA'],
    category: 'Technology',
  },
  {
    id: '9',
    title: 'Gold Hits New All-Time High Amid Geopolitical Tensions',
    summary:
      'Precious metals rallied as investors sought safe-haven assets, with gold futures climbing to record levels above $2,650 per ounce.',
    source: 'Wall Street Journal',
    sourceUrl: 'https://www.wsj.com',
    time: '11 hours ago',
    tickers: ['GC', 'GLD'],
    category: 'Commodities',
    imageUrl:
      'https://images.unsplash.com/photo-1610375461246-83df859d849d?w=400&h=300&fit=crop',
  },
  {
    id: '10',
    title:
      'Pfizer and Moderna Develop Updated COVID-19 Vaccines for Fall Season',
    summary:
      'Pharmaceutical companies announced new vaccine formulations targeting the latest coronavirus variants, with distribution expected to begin within weeks.',
    source: 'Financial Times',
    sourceUrl: 'https://www.ft.com',
    time: '12 hours ago',
    tickers: ['PFE', 'MRNA'],
    category: 'Healthcare',
  },
  {
    id: '11',
    title: 'Meta Platforms Launches AI-Powered Advertising Tools',
    summary:
      'Social media giant Meta introduced new artificial intelligence features for advertisers, aiming to improve targeting and campaign performance across its platforms.',
    source: 'Bloomberg',
    sourceUrl: 'https://www.bloomberg.com',
    time: '13 hours ago',
    tickers: ['META'],
    category: 'Technology',
  },
  {
    id: '12',
    title: 'Boeing Secures Major Aircraft Order from United Airlines',
    summary:
      'United Airlines placed an order for 100 additional aircraft, providing a boost to Boeing as the manufacturer works to ramp up production and deliveries.',
    source: 'Reuters',
    sourceUrl: 'https://www.reuters.com',
    time: '14 hours ago',
    tickers: ['BA', 'UAL'],
    category: 'Aerospace',
    imageUrl:
      'https://images.unsplash.com/photo-1436491865332-7a61a109cc05?w=400&h=300&fit=crop',
  },
  {
    id: '13',
    title: 'Walmart Raises Annual Profit Forecast on Strong Grocery Sales',
    summary:
      'The retail giant increased its full-year earnings guidance after reporting better-than-expected quarterly results driven by robust grocery and e-commerce growth.',
    source: 'CNBC',
    sourceUrl: 'https://www.cnbc.com',
    time: '15 hours ago',
    tickers: ['WMT'],
    category: 'Retail',
  },
  {
    id: '14',
    title: "Alphabet's Google Faces Antitrust Trial Over Ad Tech Dominance",
    summary:
      "The Department of Justice's case against Google over its advertising technology business enters its second week, with industry executives testifying about market practices.",
    source: 'Wall Street Journal',
    sourceUrl: 'https://www.wsj.com',
    time: '16 hours ago',
    tickers: ['GOOGL'],
    category: 'Technology',
  },
  {
    id: '15',
    title: 'Renewable Energy Stocks Rally on New Climate Legislation',
    summary:
      'Clean energy companies saw significant gains after lawmakers announced a comprehensive climate bill with substantial incentives for renewable power development.',
    source: 'Financial Times',
    sourceUrl: 'https://www.ft.com',
    time: '18 hours ago',
    tickers: ['TSLA', 'ENPH', 'SEDG'],
    category: 'Energy',
    imageUrl:
      'https://images.unsplash.com/photo-1509391366360-2e959784a276?w=400&h=300&fit=crop',
  },
  {
    id: '16',
    title: 'Apple Announces $90 Billion Share Buyback Program',
    summary:
      'Tech giant increases capital return to shareholders with one of the largest buyback programs in corporate history.',
    source: 'Bloomberg',
    sourceUrl: 'https://www.bloomberg.com',
    time: '19 hours ago',
    tickers: ['AAPL'],
    category: 'Technology',
  },
  {
    id: '17',
    title: 'Tesla Cybertruck Production Ramps Up Ahead of Schedule',
    summary:
      'Electric vehicle maker exceeds production targets for its highly anticipated pickup truck.',
    source: 'Reuters',
    sourceUrl: 'https://www.reuters.com',
    time: '20 hours ago',
    tickers: ['TSLA'],
    category: 'Automotive',
  },
  {
    id: '18',
    title: 'Microsoft Azure Gains Market Share in Cloud Computing',
    summary:
      'Cloud platform continues to close gap with Amazon Web Services as enterprise adoption accelerates.',
    source: 'CNBC',
    sourceUrl: 'https://www.cnbc.com',
    time: '21 hours ago',
    tickers: ['MSFT', 'AMZN'],
    category: 'Technology',
  },
  {
    id: '19',
    title: 'Apple iPhone 16 Pre-Orders Exceed Expectations in China',
    summary:
      'Strong demand in key Asian market signals continued momentum for flagship smartphone line.',
    source: 'Wall Street Journal',
    sourceUrl: 'https://www.wsj.com',
    time: '22 hours ago',
    tickers: ['AAPL'],
    category: 'Technology',
  },
  {
    id: '20',
    title: 'Tesla Energy Division Sees Record Solar Panel Installations',
    summary:
      'Renewable energy arm posts strongest quarter ever as residential solar demand surges.',
    source: 'Financial Times',
    sourceUrl: 'https://www.ft.com',
    time: '23 hours ago',
    tickers: ['TSLA'],
    category: 'Energy',
  },
  {
    id: '21',
    title: 'Microsoft Teams Surpasses 500 Million Monthly Active Users',
    summary:
      'Collaboration platform reaches new milestone as hybrid work drives adoption.',
    source: 'Bloomberg',
    sourceUrl: 'https://www.bloomberg.com',
    time: '1 day ago',
    tickers: ['MSFT'],
    category: 'Technology',
  },
  {
    id: '22',
    title: 'Apple Watch Series 10 Adds Advanced Health Monitoring Features',
    summary:
      'New wearable includes blood pressure tracking and sleep apnea detection capabilities.',
    source: 'Reuters',
    sourceUrl: 'https://www.reuters.com',
    time: '1 day ago',
    tickers: ['AAPL'],
    category: 'Technology',
  },
  {
    id: '23',
    title: 'Tesla Full Self-Driving Beta Expands to European Markets',
    summary:
      'Autonomous driving software becomes available to customers in Germany, France, and UK.',
    source: 'CNBC',
    sourceUrl: 'https://www.cnbc.com',
    time: '1 day ago',
    tickers: ['TSLA'],
    category: 'Automotive',
  },
  {
    id: '24',
    title: 'Microsoft Copilot Integration Drives Office 365 Subscriptions',
    summary:
      'AI-powered productivity tools fuel growth in enterprise software segment.',
    source: 'Wall Street Journal',
    sourceUrl: 'https://www.wsj.com',
    time: '1 day ago',
    tickers: ['MSFT'],
    category: 'Technology',
  },
  {
    id: '25',
    title: 'Apple Expands Services Revenue with New Subscription Bundles',
    summary:
      'Company introduces premium tier combining Apple One with additional cloud storage.',
    source: 'Financial Times',
    sourceUrl: 'https://www.ft.com',
    time: '1 day ago',
    tickers: ['AAPL'],
    category: 'Technology',
  },
  {
    id: '26',
    title: 'Tesla Megapack Battery Systems Win Major Utility Contracts',
    summary:
      'Energy storage solutions selected for grid-scale projects across three continents.',
    source: 'Bloomberg',
    sourceUrl: 'https://www.bloomberg.com',
    time: '1 day ago',
    tickers: ['TSLA'],
    category: 'Energy',
  },
  {
    id: '27',
    title: 'Microsoft Gaming Division Reports Strong Xbox Game Pass Growth',
    summary:
      'Subscription service adds 5 million new members following major game releases.',
    source: 'Reuters',
    sourceUrl: 'https://www.reuters.com',
    time: '1 day ago',
    tickers: ['MSFT'],
    category: 'Technology',
  },
  {
    id: '28',
    title: 'Apple Vision Pro Headset Launches in Asia-Pacific Markets',
    summary:
      'Mixed reality device expands availability with rollout in Japan, South Korea, and Australia.',
    source: 'CNBC',
    sourceUrl: 'https://www.cnbc.com',
    time: '2 days ago',
    tickers: ['AAPL'],
    category: 'Technology',
  },
  {
    id: '29',
    title: 'Tesla Secures Lithium Supply Deal for Next-Generation Batteries',
    summary:
      'Multi-year agreement ensures raw materials for upcoming 4680 battery cell production.',
    source: 'Wall Street Journal',
    sourceUrl: 'https://www.wsj.com',
    time: '2 days ago',
    tickers: ['TSLA'],
    category: 'Automotive',
  },
  {
    id: '30',
    title: 'Microsoft Surface Lineup Refreshed with Latest Intel Processors',
    summary:
      'Premium laptop and tablet series receives hardware upgrades across entire range.',
    source: 'Financial Times',
    sourceUrl: 'https://www.ft.com',
    time: '2 days ago',
    tickers: ['MSFT'],
    category: 'Technology',
  },
]
